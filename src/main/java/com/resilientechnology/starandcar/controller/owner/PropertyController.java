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

    @GetMapping(value = { "zip", "zip/{zip}" }, produces = "application/json")
    public List<Property> listRoomsForOwner(@PathVariable(value = "zip", required = false) String zip) {

        if (zip == null || zip.isBlank()) {
            return propertyService.roomsByZip(313001L);
        }

        try {
            Long zipCode = Long.parseLong(zip);
            return propertyService.roomsByZip(zipCode);
        } catch (NumberFormatException e) {
            // fallback when invalid zip like "alphabest"
            return propertyService.searchByText(String.valueOf(zip));
        }
    }

/*
    @GetMapping(value = "search/{searchText}",  produces = "application/json")
    public List<Property> searchByText(@PathVariable("searchText") String searchText) {
        return propertyService.searchByText(searchText);
    }
*/

    @GetMapping(value = "property/{propertyId}", produces = "application/json")
    public Property getPropertyById(@PathVariable("propertyId") Long propertyID) {
        return propertyService.getPropertyById(propertyID);
    }
}