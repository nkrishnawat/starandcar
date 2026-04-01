package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.PropertyDetailVO;
import com.resilientechnology.starandcar.entity.Property;
import com.resilientechnology.starandcar.entity.Room;
import com.resilientechnology.starandcar.repository.owner.PropertyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
public class PropertyService {

    @Autowired
    PropertyRepository propertyRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;


    public List<Property> roomsByZip(Long zip) {
        return propertyRepository.listRoomsForZip(zip);
    }

    public List<Property> searchByText(String searchByText) {
        return propertyRepository.searchByText(searchByText);
    }

    public Property getPropertyById(Long propertyID) {
        return propertyRepository.getPropertyById(propertyID);
    }

    public boolean save(PropertyDetailVO propertyDetailVO, List<MultipartFile> files) {
            List<String> fileNames = new ArrayList<>();

            /** Save Images **/
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            for (MultipartFile file : files) {
                String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
                Path path = Paths.get(uploadDir, fileName);
                try {
                    Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    System.out.printf("First: Something went wrong with file upload.");
                    throw new RuntimeException(e);
                }
                fileNames.add(fileName);
            }

            if(fileNames !=null && fileNames.size() == files.size()) {
                propertyRepository.save(to_entity(propertyDetailVO, fileNames));
            } else {
                System.out.printf("Second: Something went wrong with file upload.");
            }

            return fileNames !=null && fileNames.size() == files.size();
        }
    private Property to_entity(PropertyDetailVO propertyDetailVO, List<String> fileNames) {
        Room room = Room.builder().isAc(true)
                .propertyId(System.currentTimeMillis())
                .roomId((System.currentTimeMillis()/29)*13)
                .imageUrlS3(new HashSet<>(fileNames)).build();

        HashSet set = new HashSet();
        set.add(room);

        return Property.builder().address(propertyDetailVO.getAddress())
                .contactPhoneNo(propertyDetailVO.getPhoneno())
                .contactEmail(propertyDetailVO.getEmail())
                .notes(propertyDetailVO.getNotes())
                .description(propertyDetailVO.getDescription())
                .rooms(set).build();
    }
}
