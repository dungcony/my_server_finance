package com.datn.financeapp.module.notification.mapper;

import com.datn.financeapp.module.notification.dto.response.NotificationRes;
import com.datn.financeapp.module.notification.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationRes toRes(Notification notification);
}
