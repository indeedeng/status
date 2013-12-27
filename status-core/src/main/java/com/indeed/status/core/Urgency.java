package com.indeed.status.core;

import javax.annotation.Nonnull;

/**
 *
 */
public enum Urgency {
    /**
     * An urgency value that represents a hard dependency of the current system. If
     *  the functionality offered by the dependency is not available or is impaired, then so is the
     *  functionality of this system.
     */
     REQUIRED {
        @Override
        public CheckStatus downgradeWith (
                @Nonnull final CheckStatus system, @Nonnull final CheckStatus status
        ) {
            // Return the 'least available' status.
            return CheckStatus.min(system, status);
        }

        public String toString() {
            return "Required: Failure of this dependency would result in complete system outage";
        }
    },

    /**
     *  The MAJOR urgency value represents a functional dependency that the current system
     *   can operate without.
     */
    STRONG {
        @Override
        public CheckStatus downgradeWith (
                @Nonnull CheckStatus system, @Nonnull CheckStatus status
        ) {
            // When the
            final CheckStatus boundedStatus = status.noWorseThan(CheckStatus.MAJOR);
            return system.noBetterThan(boundedStatus);
        }

        public String toString () {
            return "Strong: Failure of this dependency would result in major functional degradation";
        }
    },

    /**
     *  The MINOR urgency value represents a functional dependency that the current system
     *   can operate without.
     */
    WEAK {
        @Override
        public CheckStatus downgradeWith (
                @Nonnull CheckStatus system, @Nonnull CheckStatus status
        ) {
            // When the
            final CheckStatus boundedStatus = status.noWorseThan(CheckStatus.MINOR);

            return system.noBetterThan(boundedStatus);
        }

        public String toString () {
            return "Weak: Failure of this dependency would result in minor functionality loss";
        }
    },

    UNKNOWN {
        @Override
        public CheckStatus downgradeWith (@Nonnull CheckStatus system, @Nonnull CheckStatus status) {
            return REQUIRED.downgradeWith(system, status);
        }

        public String toString() {
            return "Unknown";
        }
    };

    /**
     * Indicates the level to which the given <code>system</code> dependency should be
     *  downgraded based on the <code>status</code> of a dependency having this urgency
     *  level
     *
     * @param system The overall status of the system to date.
     * @param status The status of the particular dependency under evaluation.
     *
     * @return The status of the overall system with respect to the newly discovered dependency's value
     */
    public abstract CheckStatus downgradeWith (
            @Nonnull final CheckStatus system, @Nonnull final CheckStatus status
    );


    /**
     * Small collection of built-in urgency values demonstrating the common use cases for
     *  urgency evaluation.
     */
    @Deprecated // Use the urgency enum itself
    public static class BuiltIns {
        /**
         * An urgency value that represents a hard dependency of the current system. If
         *  the functionality offered by the dependency is not available or is impaired, then so is the
         *  functionality of this system.
         */
        @Deprecated // Use the urgency enum itself
        public static final Urgency REQUIRED = Urgency.REQUIRED;
        @Deprecated // Use the urgency enum itself
        public static final Urgency STRONG = Urgency.STRONG;
        @Deprecated // Use the urgency enum itself
        public static final Urgency WEAK = Urgency.WEAK;
        @SuppressWarnings({"UnusedDeclaration"})
        @Deprecated // Use the urgency enum itself
        public static final Urgency UNKNOWN = Urgency.UNKNOWN;
    }
}
