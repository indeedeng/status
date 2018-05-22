package com.indeed.status.core;

import com.indeed.status.core.AbstractDependency;
import com.indeed.status.core.CheckResult;
import com.indeed.status.core.CheckStatus;
import com.indeed.status.core.Urgency;
import org.apache.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * This dependency return the state according the average failed ratio in a given time window.
 *　@author xinjianz
 */
public abstract class SlideWindowDependency extends AbstractDependency {

    private static final Logger LOG = Logger.getLogger(SlideWindowDependency.class);

    private final EventList eventList;
    // When failed ratio is below maxOK, check status is OK.
    protected final double maxOK;
    // When failed ratio is in [maxOK, maxMinor), check status is MINOR.
    protected final double maxMinor;
    // When failed ratio is in [maxMinor, maxMajor), check status is MAJOR.
    // When failed ratio is in [maxMajor, ~), check status is OUTAGE.
    protected final double maxMajor;

    protected SlideWindowDependency(final String id,
                                    final String description,
                                    final long timeout,
                                    final long pingPeriod,
                                    final Urgency urgency,
                                    final double maxOK,
                                    final double maxMinor,
                                    final double maxMajor,
                                    final long timeInterval) {
        super(id, description, timeout, pingPeriod, urgency, DEFAULT_TYPE, DEFAULT_SERVICE_POOL);
        this.maxOK = maxOK;
        this.maxMinor = maxMinor;
        this.maxMajor = maxMajor;
        eventList = new EventList(timeInterval);
    }

    protected static class Event {
        private final double failedRatio;
        private final long time;
        public Event(final double failedRatio, final long time) {
            this.failedRatio = failedRatio;
            this.time = time;
        }

        @Override
        public String toString() {
            return "failedRatio: " + failedRatio + " ; time: " + time;
        }
    }

    private static class EventList {
        final Deque<Event> events = new ArrayDeque<>();
        final long timeInterval;
        long lastUpdate;
        double totalRatio;
        EventList(final long timeInterval) {
            // timeInterval need to be a positive number.
            this.timeInterval = (timeInterval >= 0) ? timeInterval : 1;
            lastUpdate = System.currentTimeMillis();
            totalRatio = 0;
        }

        /**
         * The x-axis is time, the y-axis is failed ratio. Draw lines between adjacent events.
         * Average failed ratio is the area under lines divides total time.
         * @param newEvent the new ping event.
         * @return average failed ratio in the window.
         */
        synchronized double addEvent(final Event newEvent) {
            // Due to the precision of double, recalculate the totalRatio every one hour.
            if ((newEvent.time - lastUpdate) <= (3600 * 1000)) {
                return addEventUsingSlideWindow(newEvent);
            } else {
                return addEventUsingRecalculate(newEvent);
            }
        }

        private double addEventUsingSlideWindow(final Event newEvent) {
            final long currentTime = newEvent.time;
            if (events.isEmpty()) {
                totalRatio = newEvent.failedRatio;
            } else {
                final long time = ((currentTime - events.getFirst().time) + 1);
                totalRatio += ((newEvent.failedRatio + events.getFirst().failedRatio) * time) / 2;
                totalRatio -= events.getFirst().failedRatio;
            }
            events.addFirst(newEvent);
            while ((events.getLast().time + timeInterval) <= currentTime) {
                final Event lastEvent = events.removeLast();
                final long time = (events.getLast().time - lastEvent.time) + 1;
                totalRatio -= ((events.getLast().failedRatio + lastEvent.failedRatio) * time) / 2;
                totalRatio += events.getLast().failedRatio;
            }
            final long time = (events.getFirst().time - events.getLast().time) + 1;
            return totalRatio / time;
        }

        // TODO Merge events with the same value to save space and time.
        private double addEventUsingRecalculate(final Event newEvent) {
            final long currentTime = newEvent.time;
            events.addFirst(newEvent);
            while ((events.getLast().time + timeInterval) <= currentTime) {
                events.removeLast();
            }
            final Iterator<Event> iterator = events.descendingIterator();
            Event preEvent = null;
            totalRatio = 0;
            while (iterator.hasNext()) {
                final Event currentEvent = iterator.next();
                if (preEvent == null) {
                    totalRatio += currentEvent.failedRatio;
                } else {
                    final long time = (currentEvent.time - preEvent.time) + 1;
                    totalRatio += ((currentEvent.failedRatio + preEvent.failedRatio) * time) / 2;
                    totalRatio -= preEvent.failedRatio;
                }
                preEvent = currentEvent;
            }
            lastUpdate = currentTime;
            final long time = (events.getFirst().time - events.getLast().time) + 1;
            return totalRatio / time;
        }

    }

    @Override
    public CheckResult call() throws Exception {
        final long start = System.currentTimeMillis();
        final double averageFailedRatio = eventList.addEvent(pingWrapper());
        final CheckStatus status;
        if (averageFailedRatio < maxOK) {
            status = CheckStatus.OK;
        } else if (averageFailedRatio < maxMinor) {
            status = CheckStatus.MINOR;
        } else if (averageFailedRatio < maxMajor) {
            status = CheckStatus.MAJOR;
        } else {
            status = CheckStatus.OUTAGE;
        }
        final long duration = System.currentTimeMillis() - start;
        final String errorMessage = formatErrorMessage(eventList.timeInterval, averageFailedRatio);
        return CheckResult.newBuilder(this, status, errorMessage)
                .setTimestamp(start)
                .setDuration(duration)
                .build();
    }

    /**
     * This function is used to test dependency.
     * @return the failed ratio of your test. In one ping, you maybe test multiple instance, the returned value can
     *         be the failed ratio of your test.
     * @throws Exception
     */
    protected abstract double ping() throws Exception;

    protected abstract String formatErrorMessage(long timeInterval, double failedRatio);

    protected Event pingWrapper() {
        final long currentTime = System.currentTimeMillis();
        double failedRatio;
        try {
            failedRatio = ping();
        } catch (final Exception e) {
            failedRatio = 1.0;
        }
        return new Event(failedRatio, currentTime);
    }

}
