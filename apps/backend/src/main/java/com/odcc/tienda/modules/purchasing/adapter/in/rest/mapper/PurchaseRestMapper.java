package com.odcc.tienda.modules.purchasing.adapter.in.rest.mapper;

import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.CreatePurchaseItemRequest;
import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.CreatePurchaseRequest;
import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.ReceivePurchaseItemRequest;
import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.ReceivePurchasePalletRequest;
import com.odcc.tienda.modules.purchasing.adapter.in.rest.request.ReceivePurchaseRequest;
import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.command.CreatePurchaseItemCommand;
import com.odcc.tienda.modules.purchasing.application.command.ReceivePurchaseCommand;
import com.odcc.tienda.modules.purchasing.application.command.ReceivePurchaseItemCommand;
import com.odcc.tienda.modules.purchasing.application.command.ReceivePurchasePalletCommand;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.util.List;
import java.util.UUID;

@Mapper(config = CentralMapperConfig.class)
public interface PurchaseRestMapper {

    CreatePurchaseCommand toCreateCommand(CreatePurchaseRequest request);

    CreatePurchaseItemCommand toCommand(CreatePurchaseItemRequest request);

    @Mapping(target = "purchaseId", source = "purchaseId")
    ReceivePurchaseCommand toReceiveCommand(
        ReceivePurchaseRequest request,
        UUID purchaseId
    );

    ReceivePurchaseItemCommand toCommand(ReceivePurchaseItemRequest request);

    ReceivePurchasePalletCommand toCommand(ReceivePurchasePalletRequest request);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<ReceivePurchaseItemCommand> toReceiveItemCommands(List<ReceivePurchaseItemRequest> requests);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<ReceivePurchasePalletCommand> toReceivePalletCommands(List<ReceivePurchasePalletRequest> requests);
}
