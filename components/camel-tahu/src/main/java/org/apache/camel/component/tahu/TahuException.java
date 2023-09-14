/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.tahu;

import org.apache.camel.RuntimeCamelException;
import org.eclipse.tahu.message.model.EdgeNodeDescriptor;

public class TahuException extends RuntimeCamelException {

    TahuException(Throwable cause) {
        super(cause);
    }

    TahuException(String message, Throwable cause) {
        super(message, cause);
    }

    TahuException(String message) {
        super(message);
    }

    TahuException(EdgeNodeDescriptor edgeNodeDescriptor, Throwable cause) {
        super(edgeNodeDescriptor.getDescriptorString(), cause);
    }

    TahuException(EdgeNodeDescriptor edgeNodeDescriptor, String message, Throwable cause) {
        super(formatEdgeNodeDescriptorMessage(edgeNodeDescriptor, message), cause);
    }

    TahuException(EdgeNodeDescriptor edgeNodeDescriptor, String message) {
        super(formatEdgeNodeDescriptorMessage(edgeNodeDescriptor, message));
    }

    private static String formatEdgeNodeDescriptorMessage(EdgeNodeDescriptor edgeNodeDescriptor, String message) {
        return String.format("%s: %s", edgeNodeDescriptor.getDescriptorString(), message);
    }
}
