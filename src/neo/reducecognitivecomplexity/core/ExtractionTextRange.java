package neo.reducecognitivecomplexity.core;

import java.util.Objects;

/**
 * Represents a text range (start and end character offsets) within a source file.
 * <p>
 * This class is used to define the boundaries of a potential code extraction (refactoring).
 * It models an inclusive interval $[start, end]$.
 * </p>
 * <p>
 * <b>Ordering:</b>
 * Ranges are ordered first by their start position (ascending). If start positions are equal,
 * the longer interval (larger end position) comes first. This sorting strategy ensures that 
 * parent intervals appear before their children, which is useful for nesting analysis.
 * </p>
 */
public class ExtractionTextRange implements Comparable<ExtractionTextRange> {

    private Integer start;
    private Integer end;

    /**
     * Constructs a new text range with the given start and end positions.
     *
     * @param start The starting character offset (inclusive).
     * @param end   The ending character offset (inclusive).
     */
    public ExtractionTextRange(Integer start, Integer end) {
        this.start = start;
        this.end = end;
    }

    /**
     * Parses a text range from a string representation.
     * <p>
     * Expected format: {@code "[start, end]"} (e.g., "[10, 50]").
     * </p>
     *
     * @param pair The string representation of the range.
     * @throws IllegalArgumentException If the format is invalid or missing the comma.
     */
    public ExtractionTextRange(String pair) {
        try {
            String clean = pair.replace("[", "").replace("]", "");
            int commaIndex = clean.indexOf(',');
            if (commaIndex == -1) {
                 throw new IllegalArgumentException("Missing comma");
            }
            this.start = Integer.valueOf(clean.substring(0, commaIndex).trim());
            this.end = Integer.valueOf(clean.substring(commaIndex + 1).trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid text range string format: " + pair, e);
        }
    }

    public Integer getStart() {
        return start;
    }

    public void setStart(Integer start) {
        this.start = start;
    }

    public Integer getEnd() {
        return end;
    }

    public void setEnd(Integer end) {
        this.end = end;
    }

    // --- Relationship Logic (Instance Methods) ---

    /**
     * Checks if this text range is fully contained within another.
     * <p>
     * $P \subseteq Q \iff P_{start} \ge Q_{start} \land P_{end} \le Q_{end}$
     * </p>
     *
     * @param other The potential container range.
     * @return true if this range is inside 'other'.
     */
    public boolean isContainedIn(ExtractionTextRange other) {
        if (other == null) return false;
        return (this.start >= other.start && this.end <= other.end);
    }

    /**
     * Checks if two text ranges are disjoint (do not touch or overlap).
     * <p>
     * $P \cap Q = \emptyset \iff Q_{start} > P_{end} \lor P_{start} > Q_{end}$
     * </p>
     *
     * @param other The other range.
     * @return true if they are completely separate in the source file.
     */
    public boolean isDisjoint(ExtractionTextRange other) {
        if (other == null) return true;
        return other.start > this.end || this.start > other.end;
    }

    /**
     * Checks if two text ranges partially overlap but neither contains the other.
     * <p>
     * Overlap implies intersection without containment.
     * </p>
     *
     * @param other The other range.
     * @return true if they strictly overlap.
     */
    public boolean overlaps(ExtractionTextRange other) {
        if (other == null) return false;
        return !isDisjoint(other) && !this.isContainedIn(other) && !other.isContainedIn(this);
    }

    // --- Static Utility Adaptors (for backward compatibility if needed) ---

    public static boolean isContained(ExtractionTextRange p, ExtractionTextRange q) {
        return p.isContainedIn(q);
    }

    public static boolean disjoint(ExtractionTextRange p, ExtractionTextRange q) {
        return p.isDisjoint(q);
    }

    public static boolean overlapping(ExtractionTextRange p, ExtractionTextRange q) {
        return p.overlaps(q);
    }

    // --- Standard Overrides ---

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ExtractionTextRange other = (ExtractionTextRange) obj;
        return Objects.equals(start, other.start) && Objects.equals(end, other.end);
    }

    @Override
    public String toString() {
        return "[" + start + ", " + end + "]";
    }

    /**
     * Compares this text range to another for ordering.
     * <p>
     * 1. Sort by Start Index (Ascending).<br>
     * 2. If Start is equal, sort by End Index (Descending).
     * </p>
     * <p>
     * This logic ensures that if Range A contains Range B, Range A will 
     * sort before Range B (assuming they share a start point or A starts earlier).
     * </p>
     */
    @Override
    public int compareTo(ExtractionTextRange other) {
        int startComparison = this.start.compareTo(other.start);
        if (startComparison != 0) {
            return startComparison;
        }
        // If starts are equal, the one with the larger end (outer container) comes FIRST.
        // So we compare other.end to this.end (Descending sort).
        return other.end.compareTo(this.end);
    }
}