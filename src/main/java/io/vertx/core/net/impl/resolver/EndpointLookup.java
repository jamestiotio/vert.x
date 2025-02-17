/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.resolver;

import io.vertx.core.net.SocketAddress;

/**
 * A lookup of an endpoint.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface EndpointLookup {

  /**
   * @return the endpoint socket address
   */
  SocketAddress address();

  /**
   * Initiate a request with the endpoint, the returned endpoint request updates the endpoint statistics
   *
   * @return the request
   */
  EndpointRequest initiateRequest();

}
