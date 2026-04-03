-- PROPERTY
CREATE TABLE IF NOT EXISTS PROPERTY (
    property_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    address VARCHAR(255),
    description VARCHAR(1000),
    notes VARCHAR(1000),
    contact_email VARCHAR(500),
    contact_phone_no VARCHAR(30)
);

-- ROOM
CREATE TABLE IF NOT EXISTS ROOM (
    room_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    property_id BIGINT,
    is_ac BOOLEAN,
    image_urls TEXT,
    CONSTRAINT fk_room_property FOREIGN KEY (property_id) REFERENCES PROPERTY(property_id) ON DELETE CASCADE
);