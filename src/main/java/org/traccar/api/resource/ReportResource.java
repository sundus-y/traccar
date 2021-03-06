/*
 * Copyright 2016 - 2018 Anton Tananaev (anton@traccar.org)
 * Copyright 2016 - 2018 Andrey Kunitsyn (andrey@traccar.org)
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Date;
import java.util.HashMap;

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.util.ByteArrayDataSource;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.Context;
import org.traccar.api.BaseResource;
import org.traccar.helper.DateUtil;
import org.traccar.model.Device;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.reports.Devices;
import org.traccar.reports.Events;
import org.traccar.reports.Summary;
import org.traccar.reports.Route;
import org.traccar.reports.Stops;
import org.traccar.reports.Trips;
import org.traccar.reports.ReportUtils;
import org.traccar.reports.model.StopReport;
import org.traccar.reports.model.SummaryReport;
import org.traccar.reports.model.TripReport;

@Path("reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReportResource extends BaseResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportResource.class);

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String CONTENT_DISPOSITION_BASE = "attachment; filename=";

    private interface ReportExecutor {
        void execute(ByteArrayOutputStream stream) throws SQLException, IOException;
    }

    private Response executeReport(
            long userId, boolean mail, ReportExecutor executor) throws SQLException, IOException {
        return executeReport(userId, mail, executor, "report.xlsx");
    }

    private Response executeReport(
            long userId, boolean mail, ReportExecutor executor, String fileName) throws SQLException, IOException {
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        if (mail) {
            new Thread(() -> {
                try {
                    executor.execute(stream);

                    MimeBodyPart attachment = new MimeBodyPart();

                    attachment.setFileName(fileName);
                    attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(
                            stream.toByteArray(), "application/octet-stream")));

                    Context.getMailManager().sendMessage(
                            userId, "Report", "The report is in the attachment.", attachment);
                } catch (SQLException | IOException | MessagingException e) {
                    LOGGER.warn("Report failed", e);
                }
            }).start();
            return Response.noContent().build();
        } else {
            try {
                executor.execute(stream);
            } catch (IllegalArgumentException exception) {
                return Response.status(404, exception.getMessage()).build();
            }
            return Response.ok(stream.toByteArray())
                    .header(HttpHeaders.CONTENT_DISPOSITION, CONTENT_DISPOSITION_BASE + fileName).build();
        }
    }

    private Response executeGovReport(
            long userId, boolean mail, ReportExecutor executor,
            String fileName, String emailSubject, Boolean isZip) throws SQLException, IOException {
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        String fileExt = isZip ? ".zip" : ".xlsx";
        String fileHeader = isZip ? "application/zip, application/octet-stream" : "application/octet-stream";
        if (mail) {
            new Thread(() -> {
                try {
                    executor.execute(stream);

                    MimeBodyPart attachment = new MimeBodyPart();

                    attachment.setFileName(fileName + fileExt);
                    attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(
                            stream.toByteArray(), "application/octet-stream")));
                    String govEmail = Context.getConfig().getString("custom.govEmail");
                    Context.getMailManager().sendMessage(
                            userId, govEmail, emailSubject + "(monitor.ethiogps.com)",
                            "Please find attached the report named: "
                                    + fileName
                                    + "<br><br> If you have any questions please reply back to this "
                                    + "email or call use at +251911-206359."
                                    + "<br><br><br> Thank you, <br> EthioGPS", attachment);
                } catch (SQLException | IOException | MessagingException e) {
                    LOGGER.warn("Report failed", e);
                }
            }).start();
            return Response.noContent().build();
        } else {
            executor.execute(stream);
            return Response.ok(stream.toByteArray())
                    .header(HttpHeaders.CONTENT_DISPOSITION, CONTENT_DISPOSITION_BASE + fileName + fileExt).build();
        }
    }

    private Response executeGovReport(
            long userId, boolean mail, ReportExecutor executor,
            String fileName, String emailSubject) throws SQLException, IOException {
        return executeGovReport(userId, mail, executor, fileName, emailSubject, false);
    }

    @Path("route")
    @GET
    public Collection<Position> getRoute(
            @QueryParam("deviceId") final List<Long> deviceIds, @QueryParam("groupId") final List<Long> groupIds,
            @QueryParam("from") String from, @QueryParam("to") String to) throws SQLException {
        return Route.getObjects(getUserId(), deviceIds, groupIds,
                DateUtil.parseDate(from), DateUtil.parseDate(to));
    }

    @Path("route")
    @GET
    @Produces(XLSX)
    public Response getRouteExcel(
            @QueryParam("deviceId") final List<Long> deviceIds, @QueryParam("groupId") final List<Long> groupIds,
            @QueryParam("from") String from, @QueryParam("to") String to, @QueryParam("mail") boolean mail)
            throws SQLException, IOException {
        return executeReport(getUserId(), mail, stream -> {
            Route.getExcel(stream, getUserId(), deviceIds, groupIds,
                    DateUtil.parseDate(from), DateUtil.parseDate(to));
        });
    }

    @Path("events")
    @GET
    public Collection<Event> getEvents(
            @QueryParam("deviceId") final List<Long> deviceIds, @QueryParam("groupId") final List<Long> groupIds,
            @QueryParam("type") final List<String> types, @QueryParam("subType") final List<String> subTypes,
            @QueryParam("from") String from, @QueryParam("to") String to) throws SQLException {
        return Events.getObjects(getUserId(), deviceIds, groupIds, types, subTypes,
                new HashMap<String, Date>() {{
                    put("from", DateUtil.parseDate(from));
                    put("to", DateUtil.parseDate(to));
                }});
    }

    @Path("events")
    @GET
    @Produces(XLSX)
    public Response getEventsExcel(
            @QueryParam("deviceId") final List<Long> deviceIds, @QueryParam("groupId") final List<Long> groupIds,
            @QueryParam("type") final List<String> types, @QueryParam("subType") final List<String> subTypes,
            @QueryParam("from") String from, @QueryParam("to") String to, @QueryParam("mail") boolean mail)
            throws SQLException, IOException {
        return executeReport(getUserId(), mail, stream -> {
            Events.getExcel(stream, getUserId(), deviceIds, groupIds, types, subTypes,
                    new HashMap<String, Date>() {{
                        put("from", DateUtil.parseDate(from));
                        put("to", DateUtil.parseDate(to));
                    }});
        }, "Device Event Report");
    }

    @Path("summary")
    @GET
    public Collection<SummaryReport> getSummary(
            @QueryParam("deviceId") final List<Long> deviceIds, @QueryParam("groupId") final List<Long> groupIds,
            @QueryParam("from") String from, @QueryParam("to") String to) throws SQLException {
        return Summary.getObjects(getUserId(), deviceIds, groupIds,
                DateUtil.parseDate(from), DateUtil.parseDate(to));
    }

    @Path("summary")
    @GET
    @Produces(XLSX)
    public Response getSummaryExcel(
            @QueryParam("deviceId") final List<Long> deviceIds, @QueryParam("groupId") final List<Long> groupIds,
            @QueryParam("from") String from, @QueryParam("to") String to, @QueryParam("mail") boolean mail)
            throws SQLException, IOException {
        return executeReport(getUserId(), mail, stream -> {
            Summary.getExcel(stream, getUserId(), deviceIds, groupIds,
                    DateUtil.parseDate(from), DateUtil.parseDate(to));
        });
    }

    @Path("trips")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<TripReport> getTrips(
            @QueryParam("deviceId") final List<Long> deviceIds, @QueryParam("groupId") final List<Long> groupIds,
            @QueryParam("from") String from, @QueryParam("to") String to) throws SQLException {
        return Trips.getObjects(getUserId(), deviceIds, groupIds,
                DateUtil.parseDate(from), DateUtil.parseDate(to));
    }

    @Path("trips")
    @GET
    @Produces(XLSX)
    public Response getTripsExcel(
            @QueryParam("deviceId") final List<Long> deviceIds, @QueryParam("groupId") final List<Long> groupIds,
            @QueryParam("from") String from, @QueryParam("to") String to, @QueryParam("mail") boolean mail)
            throws SQLException, IOException {
        return executeReport(getUserId(), mail, stream -> {
            Trips.getExcel(stream, getUserId(), deviceIds, groupIds,
                    DateUtil.parseDate(from), DateUtil.parseDate(to));
        });
    }

    @Path("stops")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<StopReport> getStops(
            @QueryParam("deviceId") final List<Long> deviceIds, @QueryParam("groupId") final List<Long> groupIds,
            @QueryParam("from") String from, @QueryParam("to") String to) throws SQLException {
        return Stops.getObjects(getUserId(), deviceIds, groupIds,
                DateUtil.parseDate(from), DateUtil.parseDate(to));
    }

    @Path("stops")
    @GET
    @Produces(XLSX)
    public Response getStopsExcel(
            @QueryParam("deviceId") final List<Long> deviceIds, @QueryParam("groupId") final List<Long> groupIds,
            @QueryParam("from") String from, @QueryParam("to") String to, @QueryParam("mail") boolean mail)
            throws SQLException, IOException {
        return executeReport(getUserId(), mail, stream -> {
            Stops.getExcel(stream, getUserId(), deviceIds, groupIds,
                    DateUtil.parseDate(from), DateUtil.parseDate(to));
        });
    }

    @Path("devices")
    @GET
    public Collection<Device> getDevices() throws SQLException {
        return Devices.getObjects(getUserId());
    }

    @Path("devices")
    @GET
    @Produces(XLSX)
    public Response getDevicesExcel(@QueryParam("mail") boolean mail)
            throws SQLException, IOException {
        return executeReport(getUserId(), mail, stream -> {
            Devices.getExcel(stream, getUserId());
        });
    }

    @Path("group_devices")
    @GET
    @Produces(XLSX)
    public Response getGroupDevicesExcel(
            @QueryParam("deviceId") final List<Long> deviceIds, @QueryParam("groupId") final List<Long> groupIds,
            @QueryParam("from") String from, @QueryParam("to") String to, @QueryParam("mail") boolean mail)
            throws SQLException, IOException {
        Set<Long> deviceIdsList = new HashSet<Long>(ReportUtils.getDeviceList(deviceIds, groupIds));
        Collection<Device> devices = Context.getDeviceManager().getItems(deviceIdsList);
        String groupName = devices.iterator().next().getGroupName();
        return executeGovReport(getUserId(), mail, stream -> {
            Devices.getGroupExcel(stream, getUserId(),
                    deviceIds, groupIds, DateUtil.parseDate(from), DateUtil.parseDate(to));
        }, "Group_Device_Report", groupName + " - Group Device Report");
    }

    @Path("individual_devices")
    @GET
    @Produces(XLSX)
    public Response getIndividualDevicesExcel(
            @QueryParam("deviceId") final List<Long> deviceIds, @QueryParam("groupId") final List<Long> groupIds,
            @QueryParam("from") String from, @QueryParam("to") String to, @QueryParam("mail") boolean mail)
            throws SQLException, IOException {
        Set<Long> deviceIdsList = new HashSet<Long>(ReportUtils.getDeviceList(deviceIds, groupIds));
        Collection<Device> devices = Context.getDeviceManager().getItems(deviceIdsList);
        Device device = devices.iterator().next();
        if (devices.size() > 1) {
            return executeGovReport(getUserId(), mail, stream -> {
                Devices.getIndividualExcelZip(stream, getUserId(), devices);
            }, devices.size() + "-Individual_Device_Reports",
                    devices.size() + "Devices - Individual Device Report", true);
        } else {
            return executeGovReport(getUserId(), mail, stream -> {
                Devices.getIndividualExcel(stream, getUserId(), device);
            }, device.getUniqueId().replaceAll("[^a-zA-Z0-9.\\-]", "_")
                    + "_Individual_Device_Report", device.getName() + " - Individual Device Report");
        }
    }

}
