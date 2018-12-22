/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.Instance;
import hudson.Extension;
import hudson.model.PeriodicWork;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.jenkins.plugins.computeengine.ComputeEngineCloud.CLOUD_ID_LABEL_KEY;
import static java.util.Collections.emptyList;

/**
 * Periodically checks if there are no lost nodes in GCP.
 * If it finds any they are deleted.
 */
@Extension
@Symbol("cleanLostNodesWork")
public class CleanLostNodesWork extends PeriodicWork {
    protected final Logger logger = Logger.getLogger(getClass().getName());

    public long getRecurrencePeriod() {
        return HOUR;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    protected void doRun() {
        getClouds().forEach(this::cleanCloud);
    }

    void cleanCloud(ComputeEngineCloud cloud) {
        List<Instance> remoteInstances = findRemoteInstances(cloud);
        Map<String, ComputeEngineInstance> localInstances = findLocalInstances(cloud);
        remoteInstances.forEach(remote -> checkOneInstance(remote, localInstances, cloud));
    }

    private void checkOneInstance(Instance remote, Map<String, ComputeEngineInstance> localInstances, ComputeEngineCloud cloud) {
        String instanceName = remote.getName();
        if (!localInstances.containsKey(instanceName)) {
            logger.log(Level.INFO, "Remote instance " + instanceName + " not found locally.., removing it");
            try {
                cloud.client.terminateInstance(cloud.projectId, remote.getZone(), instanceName);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error terminating remote instance " + instanceName, ex);
            }
        }
    }

    private List<ComputeEngineCloud> getClouds() {
        return Jenkins.getInstance().clouds.stream()
                .filter(cloud -> cloud instanceof ComputeEngineCloud)
                .collect(Collectors.toList());
    }

    private Map<String, ComputeEngineInstance> findLocalInstances(ComputeEngineCloud cloud) {
        return Jenkins.getInstance().getNodesObject()
                .getNodes()
                .stream()
                .filter(node -> node instanceof ComputeEngineInstance)
                .map(node -> (ComputeEngineInstance) node)
                .filter(node -> node.cloudName.equals(cloud.name))
                .collect(Collectors.toMap(ComputeEngineInstance::getNodeName, Function.identity()));
    }

    private List<Instance> findRemoteInstances(ComputeEngineCloud cloud) {
        Map<String, String> filterLabel = new HashMap<>();
        filterLabel.put(CLOUD_ID_LABEL_KEY, String.valueOf(cloud.name.hashCode()));
        try {
            return cloud.client.getInstancesWithLabel(cloud.projectId, filterLabel);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error finding remote instances", ex);
            return emptyList();
        }
    }
}
