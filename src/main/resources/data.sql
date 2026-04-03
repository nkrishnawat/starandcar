-- Insert Properties for Udaipur ZIP Codes
INSERT INTO PROPERTY (property_id, address, description, notes, contact_email, contact_phone_no)
VALUES
(1, '12, Gangaur Ghat Marg, Old City, Udaipur, 313001',
 'Heritage villa near Lake Pichola with traditional Rajasthani interiors, rooftop seating, and walking distance to City Palace.',
 'Old city location, narrow lanes, 8 rooms, rooftop dining, AC in 3 rooms.',
 'logspate@gmail.com', '+919001021015'),

(2, '45, Shobhagpura Circle, Udaipur, 313002',
 'Modern serviced apartment in a residential neighborhood with easy access to Fatehpura and Sukhadia Circle.',
 'Family-friendly area, parking available, 12 rooms, AC in 6 rooms.',
 'logspate@gmail.com', '+919001021015'),

(3, 'Plot 78, Hiran Magri Sector 4, Udaipur, 313001',
 'Budget-friendly stay in a well-connected locality with nearby markets and public transport access.',
 'Ideal for students & working professionals, 15 rooms, AC in 5 rooms.',
 'logspate@gmail.com', '+919001021015'),

(4, '22, University Road, near MLSU Campus, Udaipur, 313001',
 'Comfortable stay close to Mohanlal Sukhadia University with greenery and peaceful surroundings.',
 'Student-centric location, WiFi enabled, 10 rooms, AC in 4 rooms.',
 'logspate@gmail.com', '+919001021015'),

(5, 'NH-8, Near Dabok Circle, Udaipur, 313011',
 'Highway-facing property suitable for travelers with spacious rooms and easy parking access.',
 'Good for short stays, 14 rooms, ample parking, AC in 7 rooms.',
 'logspate@gmail.com', '+919001021015'),

(6, 'Industrial Area Road, Madri, Udaipur, 313001',
 'Affordable accommodation near industrial hub, ideal for business travelers and workers.',
 'Close to factories, basic amenities, 20 rooms, AC in 8 rooms.',
 'logspate@gmail.com', '+919001021015')

ON DUPLICATE KEY UPDATE
    address = VALUES(address),
    description = VALUES(description),
    notes = VALUES(notes),
    contact_email = VALUES(contact_email),
    contact_phone_no = VALUES(contact_phone_no);

-- Insert Rooms with image URLs
INSERT INTO ROOM (room_id, property_id, is_ac, image_urls)
VALUES
(101, 1, TRUE, 'https://s3.aws.com/udaipur/313001/room1a.jpg,https://s3.aws.com/udaipur/313001/room1b.jpg'),
(102, 1, FALSE, 'https://s3.aws.com/udaipur/313001/room2.jpg'),

(201, 2, TRUE, 'https://s3.aws.com/udaipur/313002/room1a.jpg,https://s3.aws.com/udaipur/313002/room1b.jpg'),
(202, 2, FALSE, 'https://s3.aws.com/udaipur/313002/room2.jpg'),

(301, 3, TRUE, 'https://s3.aws.com/udaipur/313003/room1a.jpg,https://s3.aws.com/udaipur/313003/room1b.jpg'),
(302, 3, FALSE, 'https://s3.aws.com/udaipur/313003/room2.jpg'),

(401, 4, TRUE, 'https://s3.aws.com/udaipur/313004/room1a.jpg,https://s3.aws.com/udaipur/313004/room1b.jpg'),
(402, 4, FALSE, 'https://s3.aws.com/udaipur/313004/room2.jpg'),

(501, 5, TRUE, 'https://s3.aws.com/udaipur/313011/room1a.jpg,https://s3.aws.com/udaipur/313011/room1b.jpg'),
(502, 5, FALSE, 'https://s3.aws.com/udaipur/313011/room2.jpg'),

(601, 6, TRUE, 'https://s3.aws.com/udaipur/313024/room1a.jpg,https://s3.aws.com/udaipur/313024/room1b.jpg'),
(602, 6, FALSE, 'https://s3.aws.com/udaipur/313024/room2.jpg')

ON DUPLICATE KEY UPDATE
    property_id = VALUES(property_id),
    is_ac = VALUES(is_ac),
    image_urls = VALUES(image_urls);