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
package ru.openfs.lbpay.components;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import ru.openfs.lbpay.model.PrePayment;
import ru.openfs.lbpay.model.entity.DreamkasOperation;

@CheckedTemplate
public class Templates {
    public static native TemplateInstance order(PrePayment order);

    public static native TemplateInstance pay_status(String icon, String status, String orderNumber, String message);

    public static native TemplateInstance receiptOperationStatus(DreamkasOperation receipt);

    public static native TemplateInstance notifyError(String message);
}
