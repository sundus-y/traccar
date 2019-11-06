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
import java.util.List;
import java.util.Map;
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
import org.traccar.model.Event;
import org.traccar.model.Notification;
import org.traccar.model.Position;
import org.traccar.model.Typed;
import org.traccar.notification.MessageException;


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

}
