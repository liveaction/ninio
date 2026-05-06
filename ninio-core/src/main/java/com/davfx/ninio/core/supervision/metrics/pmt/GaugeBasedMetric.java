/*
 * MetricCollector.java
 * Created on May 27, 2026, 2:58 PM
 *
 * Copyright 2026 BlueCat Networks (USA) Inc. and its affiliates and licensors. All Rights Reserved.
 *
 * BlueCat Networks and its licensors hereby assert and retain all rights, title, and interest in and to the code,
 * including any and all modifications, enhancements, or derivative works thereof (collectively, the "Code"). This
 * includes, but is not limited to, all intellectual property rights, whether registered or unregistered, associated
 * with the Code. The Code contains trade secrets and proprietary and confidential information of BlueCat Networks
 * and its licensors. It is protected under applicable worldwide copyright and trade secret laws. No rights, title,
 * or interest in the Code are transferred to any third party without the explicit written consent of BlueCat
 * Networks. Any unauthorized use, reproduction, or distribution of the Code is strictly prohibited and may result in
 * legal action.
 */
package com.davfx.ninio.core.supervision.metrics.pmt;

import io.prometheus.metrics.core.metrics.Gauge;

/**
 *
 *
 * @author Baptiste Le Bail
 */
public interface GaugeBasedMetric {

    Gauge get();
}