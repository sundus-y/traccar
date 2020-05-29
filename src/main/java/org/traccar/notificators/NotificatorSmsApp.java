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
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.WriteBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.Context;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.notification.NotificationFormatter;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
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
            String msg = NotificationFormatter.formatShortMessage(userId, event, position);
            String phone = event.getDeviceId() == 0 ? "000" : event.getDevice().getPhone();
            Map<String, Object> details = new HashMap<>();
            details.put("eventType", event.getType());
            details.put("location", position.getAddress());
            details.put("deviceId", event.getDeviceId());
            details.put("eventId", event.getId());
            sendSMS(String.valueOf(event.getDeviceId()), phone, msg, "Alert Notification", details);
        }
    }

    @Override
    public void sendAsync(long userId, Event event, Position position) {
        sendSync(userId, event, position);
    }

    public void sendSMS(String id, String phone, String msg, String msgType, Map<String, Object> otherDetails) {
        boolean smsAppProd = Context.getConfig().getBoolean("smsApp.prod");
        String queueCollectionName, metaDataCollectionName, commandType;
        ApiFuture response;
        WriteBatch batch = Context.getSmsAppDb().batch();

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("id", id);
        messageData.put("phone", phone);
        messageData.put("msg", msg);
        messageData.put("msgType", msgType);
        messageData.put("otherDetails", otherDetails);
        messageData.put("queuedTimestamp", FieldValue.serverTimestamp());
        if (msgType.matches("Command.*")) {
            queueCollectionName = smsAppProd ?  "Commands" : "Demo-Commands";
            commandType = msgType.split("\\*")[1];

            DocumentReference queuedMessageRef = Context.getSmsAppDb()
                    .collection(queueCollectionName)
                    .document(commandType);
            Map<String, Object> metaData = new HashMap<>();
            metaData.put("queuedTimestamp", FieldValue.serverTimestamp());
            queuedMessageRef.set(metaData, SetOptions.merge());
            messageData.put("commandType", commandType);
            messageData.put("queuedTimestamp", new Timestamp(System.currentTimeMillis()).toString());
            response = queuedMessageRef.update("tasks", FieldValue.arrayUnion(messageData));
        } else {
            queueCollectionName = smsAppProd ?  "QueuedMessages" : "Demo-QueuedMessages";
            metaDataCollectionName = smsAppProd ? "MetaData" : "Demo-MetaData";

            DocumentReference queuedMessageRef = Context.getSmsAppDb()
                    .collection(queueCollectionName)
                    .document();

            messageData.put("queuedTimestamp", FieldValue.serverTimestamp());
            batch.set(queuedMessageRef, messageData);

            DocumentReference queuedMessagesCountRef = Context.getSmsAppDb()
                    .collection(metaDataCollectionName)
                    .document(queueCollectionName);
            FieldValue inc = FieldValue.increment(1);
            Map<String, Object> countData = new HashMap<>();
            countData.put("count", inc);
            batch.set(queuedMessagesCountRef, countData, SetOptions.merge());

            response = batch.commit();
        }
        try {
            LOGGER.info(response.get().toString());
        } catch (Exception e) {
            LOGGER.info(e.getMessage());
        }
    }
}
