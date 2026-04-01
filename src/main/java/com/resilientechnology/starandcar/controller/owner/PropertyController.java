package com.resilientechnology.starandcar.controller.owner;

import com.resilientechnology.starandcar.entity.Property;
import com.resilientechnology.starandcar.service.owner.PropertyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("property")
public class PropertyController {

    @Autowired
    PropertyService propertyService;

    @GetMapping(value = { "search", "text/{text}" }, produces = "application/json")
    public List<Property> listRoomsForOwner(@PathVariable(value = "text", required = false) String text) {

        if (text == null || text.isBlank()) {
            return propertyService.roomsByZip(313001L);
        }

        try {
            Long zipCode = Long.parseLong(text);
            return propertyService.roomsByZip(zipCode);
        } catch (NumberFormatException e) {
            return propertyService.searchByText(String.valueOf(text));
        }
    }

    @GetMapping(value = "property/{propertyId}", produces = "application/json")
    public Property getPropertyById(@PathVariable("propertyId") Long propertyID) {
        return propertyService.getPropertyById(propertyID);
    }
}