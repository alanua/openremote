/*
 * Copyright 2016, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.model.notification;

import jsinterop.annotations.JsType;
import org.openremote.model.http.RequestParams;
import org.openremote.model.http.SuccessStatusCode;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("notification")
@JsType(isNative = true)
public interface NotificationResource {

    /**
     * Store a device token for the authenticated user.
     */
    @PUT
    @Path("token")
    @SuccessStatusCode(204)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @RolesAllowed({"write:user"})
    @SuppressWarnings("unusable-by-js")
    void storeDeviceToken(@BeanParam RequestParams requestParams,
                          @FormParam("device_id") String deviceId,
                          @FormParam("token") String token,
                          @FormParam("device_type") String deviceType);

    /**
     * Only the superuser can call this operation.
     */
    @GET
    @Path("token/{userId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({"read:admin"})
    @SuppressWarnings("unusable-by-js")
    List<DeviceNotificationToken> getDeviceTokens(@BeanParam RequestParams requestParams,
                                                  @PathParam("userId") String userId);

    /**
     * Only the superuser can call this operation.
     */
    @DELETE
    @Path("token/{userId}/device/{deviceId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({"write:admin"})
    @SuppressWarnings("unusable-by-js")
    void deleteDeviceToken(@BeanParam RequestParams requestParams,
                           @PathParam("userId") String userId,
                           @PathParam("deviceId") String deviceId);

    @POST
    @Path("alert")
    @SuccessStatusCode(204)
    @Consumes(APPLICATION_JSON)
    @RolesAllowed({"write:user"})
    @SuppressWarnings("unusable-by-js")
    void storeNotificationForCurrentUser(@BeanParam RequestParams requestParams,
                                         AlertNotification alertNotification);

    /**
     * Only the superuser can call this operation.
     */
    @POST
    @Path("alert/user/{userId}")
    @SuccessStatusCode(204)
    @Consumes(APPLICATION_JSON)
    @RolesAllowed({"write:admin"})
    @SuppressWarnings("unusable-by-js")
    void storeNotificationForUser(@BeanParam RequestParams requestParams,
                                  @PathParam("userId") String userId,
                                  AlertNotification alertNotification);

    /**
     * Only the superuser can call this operation.
     */
    @GET
    @Path("alert/user/{userId}")
    @SuccessStatusCode(200)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({"read:admin"})
    @SuppressWarnings("unusable-by-js")
    AlertNotification[] getNotificationsOfUser(@BeanParam RequestParams requestParams,
                                               @PathParam("userId") String userId);

    /**
     * Only the superuser can call this operation.
     */
    @DELETE
    @Path("alert/user/{userId}")
    @SuccessStatusCode(204)
    @RolesAllowed({"write:admin"})
    @SuppressWarnings("unusable-by-js")
    void removeNotificationsOfUser(@BeanParam RequestParams requestParams,
                                   @PathParam("userId") String userId);

    /**
     * Only the superuser can call this operation.
     */
    @DELETE
    @Path("alert/user/{userId}/{alertId}")
    @SuccessStatusCode(204)
    @RolesAllowed({"write:admin"})
    @SuppressWarnings("unusable-by-js")
    void removeNotification(@BeanParam RequestParams requestParams,
                            @PathParam("userId") String userId,
                            @PathParam("alertId") Long id);

    @GET
    @Path("alert")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({"write:user"})
    @SuppressWarnings("unusable-by-js")
    List<AlertNotification> getQueuedNotificationsOfCurrentUser(@BeanParam RequestParams requestParams);

    @DELETE
    @Path("alert/{alertId}")
    @SuccessStatusCode(204)
    @RolesAllowed({"write:user"})
    @SuppressWarnings("unusable-by-js")
    void ackNotificationOfCurrentUser(@BeanParam RequestParams requestParams,
                                      @PathParam("alertId") Long id);
}
