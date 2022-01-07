package com.indeed.status.core;

public enum CheckStatus {
    OUTAGE,
    MAJOR,
    MINOR,
    OK;

    @SuppressWarnings({"UnusedDeclaration"})
    public static CheckStatus infer(final String string) {
        for (final CheckStatus status : CheckStatus.values()) {
            if (status.name().equalsIgnoreCase(string)) {
                return status;
            }
        }

        return null;
    }

    public boolean isWorseThan(final CheckStatus other) {
        return compareTo(other) < 0;
    }

    public boolean isBetterThan(final CheckStatus other) {
        return compareTo(other) > 0;
    }

    public CheckStatus noBetterThan(final CheckStatus bound) {
        return min(this, bound);
    }

    public CheckStatus noWorseThan(final CheckStatus bound) {
        return max(this, bound);
    }

    public static CheckStatus max(final CheckStatus lhs, final CheckStatus rhs) {
        final CheckStatus result;

        if (lhs.equals(rhs)) {
            result = lhs;
        } else if (lhs.compareTo(rhs) > 0) {
            result = lhs;
        } else {
            result = rhs;
        }

        return result;
    }

    public static CheckStatus min(final CheckStatus lhs, final CheckStatus rhs) {
        final CheckStatus result;

        if (lhs.equals(rhs)) {
            result = lhs;
        } else if (lhs.compareTo(rhs) < 0) {
            result = lhs;
        } else {
            result = rhs;
        }

        return result;
    }
}
