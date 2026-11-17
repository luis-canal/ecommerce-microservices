package br.edu.atitus.analyticsapi.enums;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Regiões brasileiras segundo o IBGE e o mapeamento das UFs.
 */
public enum BrazilRegion {
    NORTE("Norte", Set.of("AC", "AM", "AP", "PA", "RO", "RR", "TO")),
    NORDESTE("Nordeste", Set.of("AL", "BA", "CE", "MA", "PB", "PE", "PI", "RN", "SE")),
    CENTRO_OESTE("Centro-Oeste", Set.of("DF", "GO", "MS", "MT")),
    SUDESTE("Sudeste", Set.of("ES", "MG", "RJ", "SP")),
    SUL("Sul", Set.of("PR", "RS", "SC")),
    UNKNOWN("Não informada", Set.of());

    private static final Map<String, BrazilRegion> BY_STATE = buildIndex();

    private final String displayName;
    private final Set<String> states;

    BrazilRegion(String displayName, Set<String> states) {
        this.displayName = displayName;
        this.states = states;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<String> getStates() {
        return states;
    }

    public static BrazilRegion fromState(String state) {
        if (state == null) {
            return UNKNOWN;
        }
        return BY_STATE.getOrDefault(state.trim().toUpperCase(Locale.ROOT), UNKNOWN);
    }

    public static String normalizeState(String state) {
        if (state == null) {
            return null;
        }
        String normalized = state.trim().toUpperCase(Locale.ROOT);
        return BY_STATE.containsKey(normalized) ? normalized : null;
    }

    private static Map<String, BrazilRegion> buildIndex() {
        java.util.HashMap<String, BrazilRegion> index = new java.util.HashMap<>();
        for (BrazilRegion region : values()) {
            for (String state : region.states) {
                index.put(state, region);
            }
        }
        return Map.copyOf(index);
    }
}
