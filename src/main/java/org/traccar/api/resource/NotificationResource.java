/*
 * Copyright 2016 - 2018 Anton Tananaev (anton@traccar.org)
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
package org.traccar.api.resource;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import org.traccar.Context;
import org.traccar.api.ExtendedObjectResource;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Notification;
import org.traccar.model.Position;
import org.traccar.model.Typed;
import org.traccar.notification.MessageException;
import org.traccar.notificators.NotificatorSmsApp;


@Path("notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NotificationResource extends ExtendedObjectResource<Notification> {

    public NotificationResource() {
        super(Notification.class);
    }

    @GET
    @Path("types")
    public Collection<Typed> get() {
        return Context.getNotificationManager().getAllNotificationTypes();
    }

    @GET
    @Path("notificators")
    public Collection<Typed> getNotificators() {
        return Context.getNotificatorManager().getAllNotificatorTypes();
    }

    @POST
    @Path("test")
    public Response testMessage()
            throws MessageException, InterruptedException, UnsupportedEncodingException {
        for (Typed method : Context.getNotificatorManager().getAllNotificatorTypes()) {
            Context.getNotificatorManager()
                    .getNotificator(method.getType())
                    .sendSync(getUserId(), new Event("test", 0), new Position(0, 0));
        }
        return Response.noContent().build();
    }

    @POST
    @Path("test/{notificator}")
    public Response testMessage(@PathParam("notificator") String notificator)
            throws MessageException, InterruptedException, UnsupportedEncodingException {
        Context.getNotificatorManager()
                .getNotificator(notificator)
                .sendSync(getUserId(), new Event("test", 0), new Position(0, 0));
        return Response.noContent().build();
    }

    @GET
    @Path("sms_app")
    public Collection<Map<String, Object>> smsAppNotifications(
            @QueryParam("deviceId") long deviceId,
            @QueryParam("from") long from,
            @QueryParam("to") long to)
            throws ExecutionException, InterruptedException {
        boolean smsAppProd = Context.getConfig().getBoolean("smsApp.prod");
        String sentCollection = smsAppProd ? "SentMessages" : "Demo-SentMessages";
        Collection<Map<String, Object>> response = new ArrayList<Map<String, Object>>();
        Date fromDate = new Date(from);
        Date toDate = new Date(to);
        List<QueryDocumentSnapshot> documents = Context.getSmsAppDb().collection(sentCollection)
                .whereEqualTo("deviceId", deviceId)
                .whereGreaterThan("sentTimestamp", fromDate)
                .whereLessThanOrEqualTo("sentTimestamp", toDate)
                .get().get().getDocuments();
        for (DocumentSnapshot doc : documents) {
            response.add(doc.getData());
        }
        return response;
    }

    @POST
    @Path("send_sms")
    public Response smsAppNotifications(LinkedHashMap<String, Object> entity)
            throws ExecutionException, InterruptedException {
        ArrayList<Integer> deviceIds = (ArrayList<Integer>) entity.get("deviceIds");
        ArrayList<Integer> groupIds = (ArrayList<Integer>) entity.get("groupIds");
        String phone = (String) entity.get("phone");
        String msg = (String) entity.get("msg");
        String cmd = (String) entity.get("cmd");
        String reason = (String) entity.get("reason");
        NotificatorSmsApp smsNotificator = (NotificatorSmsApp) Context.getNotificatorManager().getNotificator("smsApp");
        if (msg != null && !msg.isEmpty()) {
            if (deviceIds != null && !deviceIds.isEmpty()) {
                List<Long> longList = new ArrayList<Long>();
                for (Integer i: deviceIds) {
                    longList.add(i.longValue());
                }
                HashSet<Long> result = new HashSet<>();
                for (long deviceId : longList) {
                    Context.getPermissionsManager().checkDevice(getUserId(), deviceId);
                    result.add(deviceId);
                }
                sendDeviceMessage(msg, smsNotificator, result);
            } else if (groupIds != null && !groupIds.isEmpty()) {
                List<Long> longList = new ArrayList<Long>();
                for (Integer i: groupIds) {
                    longList.add(i.longValue());
                }
                HashSet<Long> result = new HashSet<>();
                for (long groupId : groupIds) {
                    for (long deviceId : Context.getPermissionsManager().getGroupDevices(groupId)) {
                        Context.getPermissionsManager().checkDevice(getUserId(), deviceId);
                        result.add(deviceId);
                    }
                }
                sendDeviceMessage(msg, smsNotificator, result);
            } else if (phone != null && !phone.isEmpty()) {
                smsNotificator.sendSMS(phone, phone, msg, "Command*DIRECT-SMS", new HashMap<>());
            }
        } else if (cmd != null && !cmd.isEmpty()) {
            Map<String, Object> otherDetails = new HashMap<>();
            Device device = Context.getDeviceManager().getById(deviceIds.get(0));
            if (cmd.equalsIgnoreCase("OVERSPEED-OFF")) {
                otherDetails.put("reason", reason);
                smsNotificator.sendSMS(device.getUniqueId(),
                        phone,
                        "AS7777AT+OVERSPEED;",
                        "Command*OVERSPEED-OFF",
                        otherDetails);
            } else {
                smsNotificator.sendSMS(phone, phone, "", "Command*DIRECT-SMS", new HashMap<>());
            }
        }
        return Response.status(200, "Message Queued").build();
    }

    private void sendDeviceMessage(String msg, NotificatorSmsApp smsNotificator, HashSet<Long> result) {
        for (Device device : Context.getDeviceManager().getItems(result)) {
            Map<String, Object> details = new HashMap<>();
            details.put("owner", device.getName());
            details.put("plateNumber", device.getPlateNumber() + " / " + device.getNewPlateNumber());
            smsNotificator.sendSMS(String.valueOf(device.getId()),
                    device.getPhone(),
                    msg,
                    "Command*DEVICE-SMS",
                    details);
        }
    }
}
