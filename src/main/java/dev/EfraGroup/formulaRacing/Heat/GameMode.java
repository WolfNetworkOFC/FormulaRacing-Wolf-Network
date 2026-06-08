package dev.EfraGroup.formulaRacing.Heat;

/**
 * Modos de jogo especiais para heats.
 * Cada modo tem mecânicas únicas e completamente diferentes de uma corrida normal.
 */
public enum GameMode {

    // ─── Modos normais ───────────────────────────────────────────────
    /**
     * Corrida padrão — quem completar todas as voltas primeiro vence.
     */
    STANDARD("Padrão", "Corrida normal — complete todas as voltas"),

    // ─── Modos malucos ───────────────────────────────────────────────
    /**
     * 🔥 BATATA QUENTE
     * Um item (batata) é passado entre os pilotos. Quem segurar por muito tempo
     * explode (DNF). A cada 15 segundos a batata passa para o piloto mais próximo.
     * O último sobrevivente vence.
     */
    HOT_POTATO("🔥 Batata Quente", "Segure a batata o mínimo possível — quem segurar muito tempo explode!"),

    /**
     * 🪑 MUSICAL CHAIR (CADEIRAS MUSICAIS)
     * Barcos são removidos a cada rodada. Quando a música para, quem não conseguir
     * um barco é eliminado. A cada rodada menos barcos disponíveis.
     * O último com barco vence.
     */
    MUSICAL_CHAIR("🪑 Cadeiras Musicais", "Quando a música para, sem barco = eliminado!"),

    /**
     * 🚩 CAPTURE THE FLAG (BANDEIRA)
     * Duas equipes. Cada equipe deve roubar a bandeira do time adversário
     * e trazer para sua base. A primeira equipe a capturar 3 bandeiras vence.
     */
    CAPTURE_THE_FLAG("🚩 Capture a Bandeira", "Roube a bandeira inimiga e traga para sua base!"),

    /**
     * ☣️ INFECÇÃO
     * Um piloto começa "infectado". Quem for tocado pelo infectado também fica
     * infectado. Infectados vão ficando mais lentos. O último não infectado vence.
     */
    INFECTION("☣️ Infecção", "Fuja dos infectados! O último sobrevivente vence!"),

    /**
     * 🔄 REVERSÃO
     * A cada 20 segundos, a direção inverte — quem estava na liderança vai para
     * último e vice-versa. Quem tiver mais pontos ao final vence.
     */
    REVERSAL("🔄 Reversão", "A classificação inverte a cada 20 segundos!"),

    /**
     * 💥 CAOS TOTAL
     * Efeitos aleatórios a cada 10 segundos: velocidade, lentidão, pulo, cegueira,
     * inversão de controles, explosão, teletransporte aleatório. Sobreviva!
     */
    TOTAL_CHAOS("💥 Caos Total", "Efeitos aleatórios a cada 10 segundos — sobreviva!"),

    /**
     * ⚔️ ELIMINAÇÃO POR EQUIPE
     * Duas equipes. A cada 30 segundos, o pior piloto de cada equipe é eliminado.
     * A equipe com pilotos restantes vence.
     */
    TEAM_ELIMINATION("⚔️ Eliminação por Equipe", "Equipes competem — o pior de cada equipe é eliminado!"),

    /**
     * 🏃 CORRIDA DE OBSTÁCULOS
     * A pista tem obstáculos que aparecem e desaparecem. Quem bater perde velocidade.
     * Primeiro a completar as voltas vence.
     */
    OBSTACLE_RACE("🏃 Corrida de Obstáculos", "Desvie dos obstáculos — quem bater fica lento!"),

    /**
     * 🥊 SUMO
     * Todos no mesmo espaço. Quem sair da arena é eliminado. Último dentro vence.
     */
    SUMO("🥊 Sumo", "Empurre os outros para fora da arena — último dentro vence!"),

    /**
     * 👁️ CORRIDA CEGA
     * A tela fica escura a cada 5 segundos por 3 segundos. Pilotos devem memorizar
     * a pista. Primeiro a completar as voltas vence.
     */
    BLIND_RACE("👁️ Corrida Cega", "A tela apaga e acende — memorize a pista!"),

    /**
     * 🏗️ CONSTRUTOR
     * A cada volta, blocos são adicionados/removidos na pista. Pilotos devem se
     * adaptar ao percurso que muda constantemente.
     */
    BUILDER("🏗️ Construtor", "A pista muda a cada volta — adapte-se!"),

    /**
     * 🎯 ALVO MÓVEL
     * Um alvo (bloco brilhante) aparece em posições aleatórias. Quem chegar mais
     * perto do alvo ganha pontos. Mais pontos ao final vence.
     */
    MOVING_TARGET("🎯 Alvo Móvel", "Colete alvos pela pista — mais pontos vence!"),

    /**
     * 🌊 MARÉ VIVA
     * A água sobe e desce periodicamente. Quando sobe, a pista fica submersa e
     * pilotos ficam lentos. Quando desce, a pista normal volta.
     */
    TIDAL_WAVE("🌊 Maré Viva", "A água sobe e desce — adapte-se às marés!"),

    /**
     * 🎰 ROLETA RUSSA
     * A cada 15 segundos, uma roleta decide: velocidade para um, lentidão para outro,
     * teletransporte, inversão de controles, ou nada. Sorte pura!
     */
    ROULETTE("🎰 Roleta Russa", "A roleta decide seu destino a cada 15 segundos!");

    private final String displayName;
    private final String description;

    GameMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Retorna true se o modo é especial (não é corrida padrão).
     */
    public boolean isSpecial() {
        return this != STANDARD;
    }

    /**
     * Retorna true se o modo é baseado em equipes.
     */
    public boolean isTeamBased() {
        return this == CAPTURE_THE_FLAG || this == TEAM_ELIMINATION;
    }

    /**
     * Retorna true se o modo é baseado em eliminação.
     */
    public boolean isElimination() {
        return this == HOT_POTATO || this == MUSICAL_CHAIR || this == INFECTION
                || this == SUMO || this == TEAM_ELIMINATION;
    }

    /**
     * Retorna true se o modo tem efeitos aleatórios.
     */
    public boolean hasRandomEffects() {
        return this == TOTAL_CHAOS || this == ROULETTE;
    }
}
