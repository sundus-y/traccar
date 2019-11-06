/*
 * Copyright 2019 Anton Tananaev (anton@traccar.org)
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
package org.traccar.notificators;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.WriteBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.Context;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.notification.NotificationFormatter;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificatorSmsApp extends Notificator {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificatorTelegram.class);

    public NotificatorSmsApp() {

    }

    @Override
    public void sendSync(long userId, Event event, Position position) {
        if (event != null && !event.isDuplicateSpeeding()) {
            try {
                event.setSmsNotificationSent(true);
                Context.getDataManager().updateObject(event);
            } catch (SQLException e) {
                LOGGER.error("Error Updating Event", e);
            }
            boolean smsAppProd = Context.getConfig().getBoolean("smsApp.prod");
            String queueCollectionName = smsAppProd ?  "QueuedMessages" : "Demo-QueuedMessages";
            String metaDataCollectionName = smsAppProd ? "MetaData" : "Demo-MetaData";

            String msg = NotificationFormatter.formatShortMessage(userId, event, position);
            String to = event.getDeviceId() == 0 ? "000" : event.getDevice().getPhone();

            WriteBatch batch = Context.getSmsAppDb().batch();
            DocumentReference queuedMessageRef = Context.getSmsAppDb()
                    .collection(queueCollectionName)
                    .document();
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("msg", msg);
            messageData.put("phone", to);
            messageData.put("eventType", event.getType());
            messageData.put("location", position.getAddress());
            messageData.put("deviceId", event.getDeviceId());
            messageData.put("queuedTimestamp", FieldValue.serverTimestamp());
            batch.set(queuedMessageRef, messageData);

            DocumentReference queuedMessagesCountRef = Context.getSmsAppDb()
                    .collection(metaDataCollectionName)
                    .document(queueCollectionName);
            FieldValue inc = FieldValue.increment(1);
            Map<String, Object> countData = new HashMap<>();
            countData.put("count", inc);
            batch.set(queuedMessagesCountRef, countData, SetOptions.merge());

            ApiFuture<List<WriteResult>> response = batch.commit();
            try {
                LOGGER.info(response.toString());
            } catch (Exception e) {
                LOGGER.info(e.getMessage());
            }
        }
    }

    @Override
    public void sendAsync(long userId, Event event, Position position) {
        sendSync(userId, event, position);
    }

}
