package com.resilientechnology.starandcar.controller.owner;

import com.resilientechnology.starandcar.record.FeedbackVO;
import com.resilientechnology.starandcar.record.PropertyDetailVO;
import com.resilientechnology.starandcar.record.PropertyPublicVO;
import com.resilientechnology.starandcar.entity.Property;
import com.resilientechnology.starandcar.service.owner.PropertyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;


@RestController
@RequestMapping("property")
public class PropertyController {

    @Autowired
    PropertyService propertyService;

    /**
     * Public search. Returns {@link PropertyPublicVO} so the owner's e-mail address is never
     * exposed - listings are contacted through STARMail by device id instead.
     */
    @GetMapping(value = { "search/{text}" }, produces = "application/json")
    public List<PropertyPublicVO> search(@PathVariable(value = "text", required = false) String text) {

        List<Property> properties;
        if (text == null || text.isBlank()) {
            properties = propertyService.roomsByZip(313001L);
        } else {
            try {
                Long zipCode = Long.parseLong(text);
                properties = propertyService.roomsByZip(zipCode);
            } catch (NumberFormatException e) {
                properties = propertyService.searchByText(text);
            }
        }

        return properties.stream().map(PropertyPublicVO::from).toList();
    }

    @GetMapping(value = "property/{propertyId}", produces = "application/json")
    public PropertyPublicVO getPropertyById(@PathVariable("propertyId") Long propertyID) {
        return PropertyPublicVO.from(propertyService.getPropertyById(propertyID));
    }

    @PostMapping(value = "publish", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PropertyService.PublishResult publish(
            @Valid @ModelAttribute PropertyDetailVO propertyDetailVO,
            @RequestParam("files") List<MultipartFile> files) {
        return propertyService.save(propertyDetailVO, files);
    }

    @GetMapping(value = "manage", produces = MediaType.APPLICATION_JSON_VALUE)
    public Property getForManagement(@RequestParam Long propertyId, @RequestParam String token) {
        return propertyService.getForManagement(propertyId, token);
    }

    @PutMapping(value = "manage", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Property update(
            @RequestParam Long propertyId,
            @RequestParam String token,
            @Valid @ModelAttribute PropertyDetailVO propertyDetailVO) {
        return propertyService.update(propertyId, token, propertyDetailVO);
    }

    @DeleteMapping("manage")
    public void delete(@RequestParam Long propertyId, @RequestParam String token) {
        propertyService.delete(propertyId, token);
    }
}