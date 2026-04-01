package com.resilientechnology.starandcar.controller.owner;

import com.resilientechnology.starandcar.PropertyDetailVO;
import com.resilientechnology.starandcar.entity.Property;
import com.resilientechnology.starandcar.service.owner.PropertyService;
import jakarta.servlet.annotation.MultipartConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("property")
public class PropertyController {

    @Autowired
    PropertyService propertyService;

    @GetMapping(value = { "search/{text}" }, produces = "application/json")
    public List<Property> search(@PathVariable(value = "text", required = false) String text) {

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

    @PostMapping(value = "publish", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public boolean publish(
            @ModelAttribute PropertyDetailVO propertyDetailVO,
            @RequestParam("files") List<MultipartFile> files) {
        return propertyService.save(propertyDetailVO, files);
    }
}