package com.stagehub.analyse.dto;

import com.stagehub.analyse.controller.InformationAnalyseController;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FournisseurIaTest {

    @Test
    void valeursDeIaFournisseur_casseEtEspacesIndifferents() {
        assertThat(FournisseurIa.depuis("ollama")).isEqualTo(FournisseurIa.OLLAMA);
        assertThat(FournisseurIa.depuis(" OpenAI ")).isEqualTo(FournisseurIa.OPENAI);
    }

    @Test
    void valeurInconnueOuAbsente_refusee() {
        assertThatThrownBy(() -> FournisseurIa.depuis("mistral")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IA_FOURNISSEUR inconnu : « mistral »");
        assertThatThrownBy(() -> FournisseurIa.depuis(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void controleur_renvoieLeFournisseurConfigure() {
        assertThat(new InformationAnalyseController("openai").information().fournisseur()).isEqualTo(FournisseurIa.OPENAI);
    }
}
