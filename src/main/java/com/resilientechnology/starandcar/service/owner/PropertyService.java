package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.entity.Property;
import com.resilientechnology.starandcar.repository.owner.PropertyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PropertyService {

    @Autowired
    PropertyRepository propertyRepository;

    public List<Property> roomsByZip(Long zip) {
        return propertyRepository.listRoomsForZip(zip);
    }

    public List<Property> searchByText(String searchByText) {
        return propertyRepository.searchByText(searchByText);
    }

    public Property getPropertyById(Long propertyID) {
        return propertyRepository.getPropertyById(propertyID);
    }

}
