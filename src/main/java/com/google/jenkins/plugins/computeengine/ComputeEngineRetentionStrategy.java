package com.google.jenkins.plugins.computeengine;

import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.CloudRetentionStrategy;

public class ComputeEngineRetentionStrategy extends CloudRetentionStrategy {
    private final boolean oneShot;

    ComputeEngineRetentionStrategy(Integer retentionTimeMinutes, boolean oneShot) {
        super(retentionTimeMinutes);
        this.oneShot = oneShot;
    }

    @Override
    public void start(AbstractCloudComputer c) {
        super.start(c);
    }

    @Override
    public long check(AbstractCloudComputer c) {
        if (!oneShot) {
            return super.check(c);
        } else {
            return 60;
        }
    }
}
