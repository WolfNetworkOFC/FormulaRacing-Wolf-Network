package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolve codigos de heat no formato {@code R<round><tipo><heat>} (ex: R1F1,
 * R2SQ3) para a entidade {@link Heats}.
 *
 * <p>O formato espelha {@link Heats#getName()}: round 1-based (indice do round
 * no mapa do schedule), tipo derivado do {@link RoundType} e numero do heat
 * 1-based (chave do mapa de heats do round). A resolucao e O(1), direto nos
 * mapas — sem rebuild de lista ordenada e sem varredura.</p>
 */
public final class HeatCodes {

    private static final Pattern CODE =
            Pattern.compile("R(\\d+)(SQ|S|P|Q|F|E)(\\d+)", Pattern.CASE_INSENSITIVE);

    private HeatCodes() {
    }

    /** Codigo parseado: round e heat 1-based, tipo em maiusculas. */
    public record Parsed(int round, String type, int heat) {
    }

    /** Parseia {@code R1F1} e afins; vazio se o formato for invalido. */
    public static Optional<Parsed> parse(String code) {
        if (code == null) {
            return Optional.empty();
        }
        Matcher matcher = CODE.matcher(code.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            int round = Integer.parseInt(matcher.group(1));
            int heat = Integer.parseInt(matcher.group(3));
            if (round <= 0 || heat <= 0) {
                return Optional.empty();
            }
            return Optional.of(new Parsed(round, matcher.group(2).toUpperCase(), heat));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Letra de tipo usada em {@link Heats#getName()} para cada
     * {@link RoundType}; {@code null} quando o tipo nao tem codigo
     * (heats desses rounds nao sao enderecaveis por codigo).
     */
    public static String typeCode(RoundType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case PRACTICE -> "P";
            case QUALIFICATION -> "Q";
            case SPRINT_QUALIFICATION -> "SQ";
            case FINAL -> "F";
            case ELIMINATION -> "E";
            case SPRINT_RACE -> "S";
            default -> null;
        };
    }

    /**
     * Resolve o codigo dentro do evento: busca O(1) no mapa de rounds e no
     * mapa de heats. A letra precisa bater com o tipo real do round
     * (R1Q1 nao resolve se o round 1 for FINAL).
     */
    public static Optional<Heats> resolve(Events event, String code) {
        Optional<Parsed> parsed = parse(code);
        if (parsed.isEmpty() || event == null || event.getEventSchedule() == null) {
            return Optional.empty();
        }
        return resolve(event, parsed.get());
    }

    /** Resolve um codigo ja parseado dentro do evento. */
    public static Optional<Heats> resolve(Events event, Parsed parsed) {
        if (event == null || parsed == null || event.getEventSchedule() == null) {
            return Optional.empty();
        }
        Map<Integer, Rounds> rounds = event.getEventSchedule().getRounds();
        if (rounds == null) {
            return Optional.empty();
        }
        Rounds round = rounds.get(parsed.round());
        if (round == null) {
            return Optional.empty();
        }
        String expectedType = typeCode(round.getType());
        if (expectedType == null || !expectedType.equals(parsed.type())) {
            return Optional.empty();
        }
        Map<Integer, Heats> heats = round.getHeats();
        if (heats == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(heats.get(parsed.heat()));
    }
}
