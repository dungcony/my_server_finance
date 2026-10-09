package com.datn.financeapp.module.notification.mapper;

import com.datn.financeapp.module.notification.dto.response.NotificationListItemResponse;
import com.datn.financeapp.module.notification.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationListItemResponse toListItemResponse(Notification notification);
}
