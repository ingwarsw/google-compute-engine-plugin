package com.google.jenkins.plugins.computeengine;

import hudson.model.Computer;
import hudson.model.Node;

public abstract class LocalNode extends Node {
    @Override
    public Computer createComputer() {
        return null;
    }
}
