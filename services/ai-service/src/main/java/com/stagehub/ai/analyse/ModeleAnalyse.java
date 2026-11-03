package com.stagehub.ai.analyse;

import com.stagehub.ai.analyse.OffreClient.OffreAAnalyser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Appel au modèle de langage par Spring AI : un seul appel produit le score, la justification et le
 * résumé. Le fournisseur (Ollama en local, ou un autre) est choisi par la configuration, pas par ce code.
 * <ul>
 *   <li>sortie structurée « native » : le schéma JSON de {@link ReponseModele} est transmis au modèle, qui ne peut
 *       produire que cette forme ;</li>
 *   <li>réponse revérifiée ({@link ReponseModele#erreurs()}) : invalide → {@link ReponseInvalideException}, et la
 *       demande sera retentée ; on préfère pas de score à un score douteux ;</li>
 *   <li>aucun outil (tool calling) n'est donné au modèle : il ne peut rien faire d'autre qu'écrire cette réponse.</li>
 * </ul>
 */
@Component
public class ModeleAnalyse {

    private static final Logger log = LoggerFactory.getLogger(ModeleAnalyse.class);

    private final ChatClient chatClient;
    private final PromptAnalyse prompts;

    /**
     * Spring AI part des options du modèle ({@code ChatModel.getOptions()} ; Ollama : OllamaChatOptions) et y pose le
     * schéma de la sortie structurée native. Un modèle aux options génériques ferait se rabattre Spring AI, en silence,
     * sur une consigne de format ajoutée au texte : AnalyseIT vérifie que le schéma part bien dans les options.
     */
    public ModeleAnalyse(ChatClient.Builder chatClientBuilder, PromptAnalyse prompts) {
        this.chatClient = chatClientBuilder.build();
        this.prompts = prompts;
    }

    /** Réponse validée du modèle, avec le nom du modèle et le nombre de jetons lus et produits. */
    public record Resultat(ReponseModele reponse, String modele, Integer jetonsLus, Integer jetonsProduits) {
    }

    public Resultat analyser(OffreAAnalyser offre, String texteCv) {
        PromptAnalyse.Messages messages = prompts.construire(offre, texteCv);
        ResponseEntity<ChatResponse, ReponseModele> reponse = chatClient.prompt()
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .system(messages.systeme())
                .user(messages.utilisateur())
                .call()
                .responseEntity(ReponseModele.class);
        ReponseModele contenu = reponse.entity();
        List<String> erreurs = contenu == null ? List.of("réponse vide") : contenu.erreurs();
        if (!erreurs.isEmpty()) {
            throw new ReponseInvalideException(String.join(" ; ", erreurs));
        }
        ChatResponseMetadata metadonnees = reponse.response() == null ? null : reponse.response().getMetadata();
        if (metadonnees == null) {
            return new Resultat(contenu, null, null, null);
        }
        Resultat resultat = new Resultat(contenu, metadonnees.getModel(),
                metadonnees.getUsage() == null ? null : metadonnees.getUsage().getPromptTokens(),
                metadonnees.getUsage() == null ? null : metadonnees.getUsage().getCompletionTokens());
        log.debug("Réponse du modèle {} : {} jetons lus, {} produits", resultat.modele(), resultat.jetonsLus(),
                resultat.jetonsProduits());
        return resultat;
    }
}
