/*
 * Copyright 2021-2024 OpenFS.RU
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.openfs.lbpay.service;

import java.util.Optional;

import jakarta.inject.Inject;
import ru.openfs.lbpay.client.lbcore.LbCoreSoapClient;

public class LbCoreService {

    @Inject
    public LbCoreSoapClient lbCoreSoapClient;

    // connect to billing
    protected String getSession() {
        return Optional.ofNullable(lbCoreSoapClient.login())
                .orElseThrow(() -> new RuntimeException("no lb session"));
    }

    protected void closeSession(String sessionId) {
        if (sessionId != null)
            lbCoreSoapClient.logout(sessionId);
    }

}
