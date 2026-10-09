package dev.kumarnenavath.shop;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can move forward, to exercise token expiry and webhook freshness without sleeping. */
public class TestClock extends Clock {

    public static final Instant START = Instant.parse("2026-10-01T10:00:00Z");

    private volatile Instant now = START;

    public void reset() { now = START; }

    public void advance(Duration duration) { now = now.plus(duration); }

    @Override
    public Instant instant() { return now; }

    @Override
    public ZoneId getZone() { return ZoneOffset.UTC; }

    @Override
    public Clock withZone(ZoneId zone) { throw new UnsupportedOperationException(); }
}
