/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.operations.trace;

import com.google.common.collect.ImmutableMap;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.OperationFinishEvent;

import java.util.HashMap;
import java.util.Map;

class SerializedOperationFinish {

    final Object id;

    final long endTime;

    final Object result;
    final String resultClassName;

    final String failureMsg;

    SerializedOperationFinish(BuildOperationDescriptor descriptor, OperationFinishEvent finishEvent) {
        this.id = ((OperationIdentifier) descriptor.getId()).getId();
        this.endTime = finishEvent.getEndTime();
        this.result = transform(finishEvent.getResult());
        this.resultClassName = result == null ? null : finishEvent.getResult().getClass().getName();
        this.failureMsg = finishEvent.getFailure() == null ? null : finishEvent.getFailure().toString();
    }

    private Object transform(Object details) {
        /**
         * TODO verify if relying on reflection or shuffling around dependencies makes most sense here.
         * Avoiding referencing ResolveDependenciesBuildOperationType here as this brings dependency to
         * dependencyManagement to core.
         * Probably tracing should live somewhere else as this could be a recurring problem.
         * */
        if (details.getClass().getSimpleName().equals("ResolveDependenciesBuildOperationType.Result")) {
            Map<String, Object> map = new HashMap<String, Object>();
            // TODO add valuable information here
            return map;
        }
        return details;
    }

    SerializedOperationFinish(Map<String, ?> map) {
        this.id = map.get("id");
        this.endTime = (Long) map.get("endTime");
        this.result = map.get("result");
        this.resultClassName = (String) map.get("resultClassName");
        this.failureMsg = (String) map.get("failure");
    }

    Map<String, ?> toMap() {
        ImmutableMap.Builder<String, Object> map = ImmutableMap.builder();

        // Order is optimised for humans looking at the log.

        map.put("id", id);

        if (result != null) {
            map.put("result", result);
            map.put("resultClassName", resultClassName);
        }

        if (failureMsg != null) {
            map.put("failure", failureMsg);
        }

        map.put("endTime", endTime);

        return map.build();
    }

}
