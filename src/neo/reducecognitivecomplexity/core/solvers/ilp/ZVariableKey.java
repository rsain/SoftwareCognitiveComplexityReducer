package neo.reducecognitivecomplexity.core.solvers.ilp;

import java.util.Objects;

/**
 * Represents a pair of vertex indices (j,i) used as a key for Z variables.
 * <p>
 * Z_{j,i} represents the linearization of X_j * X_i, where j is an ancestor of i.
 * This class provides proper equals/hashCode for use as a map key.
 * </p>
 */
public class ZVariableKey {
    private final int j; // ancestor index
    private final int i; // descendant index

    public ZVariableKey(int j, int i) {
        this.j = j;
        this.i = i;
    }

    public int getJ() {
        return j;
    }

    public int getI() {
        return i;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZVariableKey that = (ZVariableKey) o;
        return j == that.j && i == that.i;
    }

    @Override
    public int hashCode() {
        return Objects.hash(j, i);
    }

    @Override
    public String toString() {
        return "Z_" + j + "_" + i;
    }
}