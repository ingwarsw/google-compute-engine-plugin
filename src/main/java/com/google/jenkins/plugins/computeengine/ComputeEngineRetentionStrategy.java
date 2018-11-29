package com.google.jenkins.plugins.computeengine;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Queue;
import hudson.slaves.EphemeralNode;
import hudson.slaves.RetentionStrategy;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MINUTES;

public class ComputeEngineRetentionStrategy extends RetentionStrategy<ComputeEngineComputer> implements ExecutorListener {

    private static final Logger LOGGER = Logger.getLogger(ComputeEngineRetentionStrategy.class.getName());
    private static int DEFAULT_IDLEMINUTES = 10;

    private int idleMinutes = DEFAULT_IDLEMINUTES;

    /**
     * Creates the retention strategy.
     *
     * @param idleMinutes number of minutes of idleness after which to kill the slave; serves a backup in case the strategy fails to detect the end of a task
     */
    @DataBoundConstructor
    public ComputeEngineRetentionStrategy(int idleMinutes) {
        this.idleMinutes = idleMinutes;
    }

    public int getIdleMinutes() {
        if (idleMinutes < 1) {
            idleMinutes = DEFAULT_IDLEMINUTES;
        }
        return idleMinutes;
    }

    @Override
    public long check(@Nonnull ComputeEngineComputer c) {
        // When the slave is idle we should disable accepting tasks and check to see if it is already trying to
        // terminate. If it's not already trying to terminate then lets terminate manually.
        if (c.isIdle()) {
            final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
            if (idleMilliseconds > MINUTES.toMillis(getIdleMinutes())) {
                LOGGER.log(Level.FINE, "Disconnecting {0}", c.getName());
                done(c);
            }
        }

        // Return one because we want to check every minute if idle.
        return 1;
    }

    @Override
    public void start(ComputeEngineComputer c) {
        if (c.getNode() instanceof EphemeralNode) {
            throw new IllegalStateException("May not use OnceRetentionStrategy on an EphemeralNode: " + c);
        }
        c.connect(true);
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        done(executor);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        done(executor);
    }

    private void done(Executor executor) {
        final ComputeEngineComputer c = (ComputeEngineComputer) executor.getOwner();
        Queue.Executable exec = executor.getCurrentExecutable();
        LOGGER.log(Level.FINE, "terminating {0} since {1} seems to be finished", new Object[]{c.getName(), exec});
        done(c);
    }

    private synchronized void done(final ComputeEngineComputer c) {
        c.setAcceptingTasks(false); // just in case
        Computer.threadPoolForRemoting.submit(new Runnable() {
            @Override
            public void run() {
                Queue.withLock(new Runnable() {
                    @Override
                    public void run() {
                        ComputeEngineInstance node = c.getNode();
                        if (node != null) {
//                            node.terminate(c.getListener());
                        }
                    }
                });
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ComputeEngineRetentionStrategy that = (ComputeEngineRetentionStrategy) o;

        return idleMinutes == that.idleMinutes;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
        @Override
        public String getDisplayName() {
            return "Use container only once";
        }

        public FormValidation doCheckIdleMinutes(@QueryParameter String value) {
            return FormValidation.validatePositiveInteger(value);
        }
    }
}
