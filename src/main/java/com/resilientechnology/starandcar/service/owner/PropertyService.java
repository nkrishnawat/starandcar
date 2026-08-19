package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.record.PropertyDetailVO;
import com.resilientechnology.starandcar.entity.Property;
import com.resilientechnology.starandcar.entity.Room;
import com.resilientechnology.starandcar.repository.owner.PropertyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.HexFormat;
import java.util.stream.Collectors;


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

    public PublishResult save(PropertyDetailVO propertyDetailVO, List<MultipartFile> files) {
            List<String> fileNames = new ArrayList<>();
            String manageToken = generateToken();

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
                Property property = to_entity(propertyDetailVO, fileNames, hashToken(manageToken));
                propertyRepository.save(property);
                return new PublishResult(property.getPropertyId(), manageToken);
            } else {
                System.out.printf("Second: Something went wrong with file upload.");
            }

            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save uploaded files");
        }
    public Property getForManagement(Long propertyId, String token) {
        Property property = propertyRepository.getPropertyById(propertyId);
        verifyToken(property, token);
        return property;
    }

    public Property update(Long propertyId, String token, PropertyDetailVO details) {
        Property property = propertyRepository.getPropertyById(propertyId);
        verifyToken(property, token);
        propertyRepository.update(propertyId, details);
        return propertyRepository.getPropertyById(propertyId);
    }

    public void delete(Long propertyId, String token) {
        Property property = propertyRepository.getPropertyById(propertyId);
        verifyToken(property, token);
        propertyRepository.delete(propertyId);
    }

    private void verifyToken(Property property, String token) {
        if (token == null || token.isBlank() || property.getManageTokenHash() == null
                || !MessageDigest.isEqual(property.getManageTokenHash().getBytes(StandardCharsets.UTF_8),
                hashToken(token).getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid management link");
        }
    }

    private String generateToken() {
        byte[] token = new byte[32];
        new SecureRandom().nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to create management token", e);
        }
    }

    private Property to_entity(PropertyDetailVO propertyDetailVO, List<String> fileNames, String tokenHash) {
        Long propertyID = System.currentTimeMillis();
        Room room = Room.builder().isAc(true)
                .propertyId(propertyID)
                .roomId((System.currentTimeMillis()/29)*13)
                .imageUrlS3(fileNames.stream().map(fName ->
                        uploadDir + "/" + fName).collect(Collectors.toList())).build();

        HashSet set = new HashSet();
        set.add(room);

        return Property.builder()
                .propertyId(propertyID)
                .address(propertyDetailVO.getAddress())
                .contactPhoneNo(propertyDetailVO.getPhoneno())
                .contactEmail(propertyDetailVO.getEmail())
                .notes(propertyDetailVO.getNotes())
                .description(propertyDetailVO.getDescription())
                .manageTokenHash(tokenHash)
                .rooms(new ArrayList<>(set)).build();
    }

    public record PublishResult(Long propertyId, String manageToken) {}
}