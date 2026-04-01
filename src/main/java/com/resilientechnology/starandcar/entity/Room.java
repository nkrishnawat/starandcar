package com.resilientechnology.starandcar.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
@EqualsAndHashCode
public class Room {
    @Id
    Long roomId;

    @Column("property_id")
    Long propertyId;

    @Column("is_ac")
    boolean isAc;

    @Column("image_urls")
    List<String> imageUrlS3;   // mapped to room_image_url_s3 table

}