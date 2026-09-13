package com.datn.financeapp.notification.mapper;

import com.datn.financeapp.notification.dto.response.NotificationListItemResponse;
import com.datn.financeapp.notification.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationListItemResponse toListItemResponse(Notification notification);
}
