package neo.reducecognitivecomplexity.core;

import java.util.Objects;

/**
 * Represents a text range (start and end character offsets) within a source file.
 * Defines boundaries for code extraction and models an inclusive interval [start, end].
 */
public class ExtractionTextRange implements Comparable<ExtractionTextRange> {

    private Integer start;
    private Integer end;

    public ExtractionTextRange(Integer start, Integer end) {
        this.start = start;
        this.end = end;
    }

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

    public Integer getLength() {
        return this.end - this.start;
    }

    public boolean isContainedIn(ExtractionTextRange other) {
        if (other == null) return false;
        return (this.start >= other.start && this.end <= other.end);
    }

    public boolean isDisjoint(ExtractionTextRange other) {
        if (other == null) return true;
        return other.start > this.end || this.start > other.end;
    }

    public boolean overlaps(ExtractionTextRange other) {
        if (other == null) return false;
        return !isDisjoint(other) && !this.isContainedIn(other) && !other.isContainedIn(this);
    }

    public static boolean isContained(ExtractionTextRange p, ExtractionTextRange q) {
        return p.isContainedIn(q);
    }

    public static boolean disjoint(ExtractionTextRange p, ExtractionTextRange q) {
        return p.isDisjoint(q);
    }

    public static boolean overlapping(ExtractionTextRange p, ExtractionTextRange q) {
        return p.overlaps(q);
    }

    /**
     * Helper overload to compare two ExtractionTextRange instances directly.
     */
    public static int compareExecutionOrder(ExtractionTextRange rA, ExtractionTextRange rB) {
        if (rA == null && rB == null) return 0;
        if (rA == null) return 1;
        if (rB == null) return -1;
        return compareExecutionOrder(rA.getStart(), rA.getLength(), rB.getStart(), rB.getLength());
    }

    /**
     * Compares two ranges for execution order when applying an extract method refactoring:
     * 1. Child before Parent (nesting).
     * 2. Shorter length before longer length (coincident start).
     * 3. Bottom-to-Top in file (highest start offset first).
     */
    public static int compareExecutionOrder(int startA, int lengthA, int startB, int lengthB) {
        int endA = startA + lengthA;
        int endB = startB + lengthB;

        boolean aInsideB = (startA >= startB && endA <= endB) && !(startA == startB && endA == endB);
        boolean bInsideA = (startB >= startA && endB <= endA) && !(startA == startB && endA == endB);

        if (aInsideB) return -1;
        if (bInsideA) return 1;

        if (startA == startB) {
            return Integer.compare(lengthA, lengthB);
        }

        return Integer.compare(startB, startA);
    }

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

    @Override
    public int compareTo(ExtractionTextRange other) {
        int startComparison = this.start.compareTo(other.start);
        if (startComparison != 0) {
            return startComparison;
        }
        return other.end.compareTo(this.end);
    }
}