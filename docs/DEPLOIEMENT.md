# Déployer StageHub sur un serveur

Le serveur fait tourner **exactement le même `docker-compose.yml` qu'en local**, complété par
`docker-compose.prod.yml` : Caddy en façade (HTTPS automatique) et aucun autre port exposé.

```
Internet ──443/80──▶ Caddy (HTTPS) ──▶ Nginx (Angular + /api + /mail/) ──▶ api-gateway ──▶ microservices ──▶ PostgreSQL
                                                                                                  ├──▶ Kafka
                                                                                                  └──▶ Mailpit (SMTP)
```

- **Option principale : Oracle Cloud Always Free** (ARM Ampere, 2 cœurs, 12 Go) — gratuit.
- **Alternative : Hetzner Cloud** (section [Plan B](#plan-b--hetzner-cloud)) — quelques euros par mois.

> Toutes les images utilisées existent en **arm64** (vérifié le 2026-10-17) : postgres:17-alpine, mailpit, eclipse-temurin 21,
> node:24-alpine, nginx-unprivileged, caddy, ainsi qu'ollama et kafka. Sur un serveur ARM, les images
> des microservices et du frontend sont construites **sur le serveur**, donc directement en arm64.

---

## 1. Ce qu'il faut avant de commencer

| Élément | Pourquoi | Gratuit ? |
|---|---|---|
| Compte **Oracle Cloud** | Héberger la machine | Oui (carte bancaire demandée pour vérifier l'identité) |
| Un **nom de domaine** | HTTPS : Let's Encrypt délivre des certificats pour un nom, pas pour une IP | [DuckDNS](https://www.duckdns.org) (`stagehub.duckdns.org`) ou un domaine à ~10 €/an |
| Une **clé SSH** | Se connecter au serveur sans mot de passe | Oui (`ssh-keygen -t ed25519`) |
| Un accès en lecture au dépôt GitHub **privé** | Cloner le code sur le serveur | Oui : *deploy key* en lecture seule (étape 6) |

---

## 2. Créer la machine (Oracle Cloud)

1. **Compute → Instances → Create instance**.
2. **Image** : Canonical **Ubuntu 24.04** (version aarch64).
3. **Shape** : `VM.Standard.A1.Flex` (Ampere), **2 OCPU, 12 Go** — c'est la totalité de l'offre Always Free ARM depuis juin 2026.
4. **Réseau** : VCN par défaut, sous-réseau **public**, « Assign a public IPv4 address » coché.
5. **Clé SSH** : coller votre clé publique (`~/.ssh/id_ed25519.pub`).
6. **Disque de démarrage** : 50 Go suffisent (l'offre gratuite inclut 200 Go de stockage bloc au total).

> *Out of host capacity* : la capacité ARM gratuite est souvent saturée. Réessayer plus tard, ou dans un autre
> *availability domain* de la région.

---

## 3. Ouvrir les ports : deux pare-feu à régler

Sur Oracle, le trafic traverse **deux** filtres : celui du réseau virtuel (VCN), puis celui du système de la machine.
Un port doit être ouvert **dans les deux**.

### 3.1 Règles réseau de la VCN (console Oracle)

**Networking → Virtual Cloud Networks → (votre VCN) → Security Lists → Default Security List → Add Ingress Rules** :

| Source CIDR | Protocole | Port destination | Rôle |
|---|---|---|---|
| `0.0.0.0/0` | TCP | 80 | HTTP (Let's Encrypt, puis redirection vers HTTPS) |
| `0.0.0.0/0` | TCP | 443 | HTTPS |
| `0.0.0.0/0` | UDP | 443 | HTTP/3 (facultatif) |
| votre IP `/32` (idéalement) | TCP | 22 | SSH — déjà présente par défaut, ouverte à tous |

Ne **pas** ouvrir 5432 (PostgreSQL), 1025/8025 (Mailpit) ni 8090 : en production, ils ne sont pas publiés.

### 3.2 Pare-feu de la machine : ufw

Les images Ubuntu d'Oracle sont livrées avec des règles **iptables** (paquet `netfilter-persistent`) qui rejettent tout sauf
SSH. On les remplace par **ufw**, plus lisible. Se connecter d'abord : `ssh ubuntu@<IP_PUBLIQUE>`.

```bash
sudo apt-get update && sudo apt-get install -y ufw

# 1. Autoriser SSH AVANT d'activer ufw (sinon : perte d'accès à la machine)
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
sudo ufw default deny incoming
sudo ufw default allow outgoing

# 2. Désactiver les règles iptables d'origine d'Oracle (sauvegardées, pas supprimées)
sudo systemctl disable --now netfilter-persistent
sudo mv /etc/iptables/rules.v4 /etc/iptables/rules.v4.oracle-origine

# 3. Activer ufw puis redémarrer pour repartir de règles propres
sudo ufw enable
sudo reboot
```

> ⚠️ Ces règles iptables d'Oracle peuvent évoluer selon la version de l'image : vérifier `sudo iptables -L INPUT -n` avant
> l'étape 2. En cas de blocage SSH, la **console série** d'Oracle (page de l'instance) permet de reprendre la main.

> 💡 **Docker et ufw** : un port publié par Docker (`ports:`) contourne ufw (Docker écrit ses propres règles iptables).
> C'est pourquoi `docker-compose.prod.yml` ne publie **que** les ports 80/443 de Caddy : les autres services restent
> dans le réseau Docker interne, quelles que soient les règles ufw.

---

## 4. Installer Docker

```bash
# Dépôt officiel Docker (fonctionne en arm64)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER      # utiliser docker sans sudo (se reconnecter ensuite)
docker compose version             # vérifier : Docker Compose v2 ou plus
```

Mises à jour de sécurité automatiques du système :

```bash
sudo apt-get install -y unattended-upgrades && sudo dpkg-reconfigure -plow unattended-upgrades
```

---

## 5. Nom de domaine

- **DuckDNS** (gratuit) : créer `stagehub` sur duckdns.org et y saisir l'IP publique de l'instance → `stagehub.duckdns.org`.
- **Domaine personnel** : enregistrement DNS de type `A` pointant vers l'IP publique.

Vérifier depuis votre poste : `nslookup stagehub.duckdns.org` doit renvoyer l'IP du serveur.

---

## 6. Récupérer le code (dépôt privé)

Une **deploy key** donne au serveur un accès en lecture seule à ce seul dépôt :

```bash
ssh-keygen -t ed25519 -f ~/.ssh/stagehub_deploy -N ""   # sur le serveur
cat ~/.ssh/stagehub_deploy.pub
```

GitHub → dépôt → **Settings → Deploy keys → Add deploy key** (coller la clé, *Allow write access* décoché). Puis :

```bash
cat >> ~/.ssh/config <<'EOF'
Host github.com
  IdentityFile ~/.ssh/stagehub_deploy
EOF
git clone git@github.com:zakaech/stagehub.git && cd stagehub
```

---

## 7. Configurer `.env` (secrets de production)

```bash
cp .env.example .env
chmod 600 .env          # lisible par vous seul
nano .env
```

| Variable | Valeur sur le serveur |
|---|---|
| `DOMAINE` | `stagehub.duckdns.org` (à ajouter) |
| `PUBLIC_URL` | `https://stagehub.duckdns.org` |
| `DB_PASSWORD` | `openssl rand -base64 24` |
| `AUTH_DB_PASSWORD` | `openssl rand -base64 24` (base d'auth-service) |
| `JWT_PRIVATE_KEY` | Clé privée d'auth-service (PKCS#8 en Base64), à sauvegarder : la perdre déconnecte tout le monde. Commande ci-dessous |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Compte admin (mot de passe fort) |
| `COMPOSE_PROFILES` | `demo` pour la démo (comptes de démo créés par `demo-seeder`) ; vide pour une vraie production |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SWAGGER_ENABLED` | `true` pour la démo, `false` sinon |
| `LOG_FORMAT` | `ecs` (logs JSON) |
| `MAILPIT_UI_AUTH` | `demo:<mot de passe>` : protège https://domaine/mail/ |
| `DEMO_PASSWORD` | Mot de passe des comptes de démo, communiqué aux recruteurs |

Clé `JWT_PRIVATE_KEY` (OpenSSL 3 : `genpkey` seul écrit du PKCS#1 en DER, d'où la conversion `pkcs8`) :

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 -w0
```

**Emails** : par défaut, Mailpit capture tout ; les comptes de démo ont des adresses fictives (`@etu.demo`), qu'un vrai
serveur SMTP ferait rebondir. Pour de vrais utilisateurs, renseigner `SMTP_HOST`, `SMTP_PORT`, `MAIL_USERNAME`,
`MAIL_PASSWORD`, `MAIL_SMTP_AUTH=true`, `MAIL_STARTTLS=true` (ex. **Brevo**, gratuit jusqu'à 300 emails/jour, avec un
domaine d'envoi vérifié : enregistrements DNS SPF et DKIM).

---

## 8. Lancer

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps        # tous "healthy" / "running"
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f caddy api-gateway candidature-service
```

Au premier démarrage, Caddy obtient le certificat HTTPS (quelques secondes ; le port 80 doit être joignable).

- Application : `https://<DOMAINE>`
- Emails (démo) : `https://<DOMAINE>/mail/` (identifiants de `MAILPIT_UI_AUTH`)
- API documentée : `https://<DOMAINE>/swagger-ui.html` (si `SWAGGER_ENABLED=true`)

Astuce : `alias dc='docker compose -f docker-compose.yml -f docker-compose.prod.yml'` dans `~/.bashrc`.

---

## 9. Mettre à jour

```bash
git pull
dc up -d --build        # reconstruit les images modifiées, recrée les conteneurs ; les volumes (base, CV) sont conservés
docker image prune -f   # supprime les anciennes images inutilisées
```

Le pipeline Jenkins peut automatiser ces étapes (images construites pour **arm64**).

---

## 10. Sauvegardes

Les données vivent dans deux volumes : `postgres-data` (base) et `cv-data` (CV).

```bash
mkdir -p ~/sauvegardes
# Base de données (export SQL compressé)
dc exec -T postgres pg_dump -U stagehub stagehub | gzip > ~/sauvegardes/stagehub-$(date +%F).sql.gz
# CV (archive du volume via un conteneur temporaire)
docker run --rm -v stagehub_cv-data:/cv:ro -v ~/sauvegardes:/sortie alpine \
  tar czf /sortie/cv-$(date +%F).tar.gz -C /cv .
```

À planifier avec `crontab -e` (ex. chaque nuit à 3 h) et à copier **hors du serveur** : une sauvegarde sur la même machine
ne protège pas d'une perte de la machine.

---

## 11. Points d'attention propres à Oracle Always Free

- **Instances inactives récupérées** : Oracle peut arrêter une instance Always Free si, sur 7 jours, l'usage reste sous
  20 % pour le CPU, le réseau **et** la mémoire (critère mémoire propre aux instances ARM). Sans Ollama, StageHub
  utilise ~1,5 Go sur 12 (≈ 12 %) : risque réel sur une démo peu visitée. Avec Ollama (3 à 4 Go), le critère
  mémoire est dépassé. Passer le compte en *Pay As You Go* (toujours gratuit dans les limites) est souvent cité comme
  évitant cette récupération — **à vérifier** dans la documentation Oracle.
- **Limites** : rester à 2 OCPU / 12 Go au total ; au-delà, un compte gratuit voit ses instances arrêtées, un compte
  *Pay As You Go* est facturé.
- **Région** : les ressources Always Free sont dans la *home region* choisie à l'inscription (non modifiable).

---

## Plan B : Hetzner Cloud

Même procédure, avec trois différences :

1. **Machine** : Cloud Console → *Add Server* → Ubuntu 24.04.
   - **CAX21** (ARM, 4 vCPU, 8 Go) ou **CX33** (x86, 4 vCPU, 8 Go, ~8,5–9 € HT/mois en 2026) pour une démo sans Ollama ;
   - **CX43** (16 Go, ~16 € HT/mois) ou un CAX équivalent pour la pile complète (microservices, Kafka, Ollama).
     Le redimensionnement se fait en quelques clics (*Rescale*).
2. **Pare-feu réseau** : *Firewalls* → règles entrantes TCP 22 (votre IP), TCP 80, TCP 443, UDP 443 → appliquer au serveur.
   C'est l'équivalent des règles de la VCN Oracle ; ufw s'active ensuite comme à l'étape 3.2 (sans l'étape iptables
   propre à Oracle).
3. **Pas de récupération d'instance inactive**, facturation à l'heure plafonnée au mois.

> Les prix Hetzner ont été relevés plusieurs fois en 2026 : vérifier la page officielle avant de commander.

---

## Récapitulatif des coûts

| | Sans Ollama | Pile complète (microservices, Kafka, Ollama) |
|---|---|---|
| Oracle Always Free (2 OCPU, 12 Go ARM) | 0 € | 0 € (12 Go suffisent si les services restent sobres) |
| Hetzner | ~9 €/mois (8 Go) | ~16 €/mois (16 Go) |
| Domaine | 0 € (DuckDNS) | 0 € (DuckDNS) |
| Emails | 0 € (Mailpit ; Brevo gratuit si besoin) | 0 € |
