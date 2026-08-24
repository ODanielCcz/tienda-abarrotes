package com.odcc.tienda.modules.inventory.adapter.in.rest.mapper;

import com.odcc.tienda.modules.inventory.adapter.in.rest.request.CreateInventoryReceiptRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.InventoryReceiptItemRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.request.InventoryReceiptPalletRequest;
import com.odcc.tienda.modules.inventory.adapter.in.rest.response.InventoryReceiptItemResponse;
import com.odcc.tienda.modules.inventory.adapter.in.rest.response.InventoryReceiptPalletResponse;
import com.odcc.tienda.modules.inventory.adapter.in.rest.response.InventoryReceiptResponse;
import com.odcc.tienda.modules.inventory.application.command.CreateInventoryReceiptCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryReceiptItemCommand;
import com.odcc.tienda.modules.inventory.application.command.InventoryReceiptPalletCommand;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceipt;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceiptItem;
import com.odcc.tienda.modules.inventory.application.model.InventoryReceiptPallet;
import com.odcc.tienda.shared.infrastructure.mapping.CentralMapperConfig;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueMappingStrategy;

import java.util.List;

@Mapper(config = CentralMapperConfig.class)
public interface InventoryReceiptRestMapper {

    CreateInventoryReceiptCommand toCommand(CreateInventoryReceiptRequest request);

    InventoryReceiptItemCommand toCommand(InventoryReceiptItemRequest request);

    InventoryReceiptPalletCommand toCommand(InventoryReceiptPalletRequest request);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<InventoryReceiptItemCommand> toItemCommands(List<InventoryReceiptItemRequest> requests);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<InventoryReceiptPalletCommand> toPalletCommands(List<InventoryReceiptPalletRequest> requests);

    InventoryReceiptResponse toResponse(InventoryReceipt receipt);

    InventoryReceiptItemResponse toResponse(InventoryReceiptItem item);

    InventoryReceiptPalletResponse toResponse(InventoryReceiptPallet pallet);
}
