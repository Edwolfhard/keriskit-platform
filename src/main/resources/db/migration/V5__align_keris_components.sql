-- Align the component collection with the physical kit.
-- Keep the existing Sarung record.
-- Replace the old Batang Serunai entry with Tapak Kasut.

UPDATE keris_components
SET slug = 'tapak-kasut',
    name = 'Tapak Kasut',
    description = 'The end piece of the sheath. Explore its form and its position within the assembled kit.'
WHERE slug = 'batang-serunai';


-- Standardise component names and introductory text in English.

UPDATE keris_components
SET name = 'Hulu',
    description = 'The hilt of the keris. Observe its shape, surface details, and connection to the other components.'
WHERE slug = 'hulu';

UPDATE keris_components
SET name = 'Pendokok',
    description = 'The collar at the base of the hilt. Look closely at its details and its position beneath the hulu.'
WHERE slug = 'pendokok';

UPDATE keris_components
SET name = 'Sampir',
    description = 'The broad upper section of the sheath. Observe its outline and how it connects with the sheath body.'
WHERE slug = 'sampir';

UPDATE keris_components
SET name = 'Sarung',
    description = 'The sheath that houses the blade. Explore its form and its relationship to the other parts of the kit.'
WHERE slug = 'sarung';

UPDATE keris_components
SET name = 'Bilah',
    description = 'The blade of the keris. Observe its outline and visible surface details as an individual component.'
WHERE slug = 'bilah';

UPDATE keris_components
SET name = 'Tapak Kasut',
    description = 'The end piece of the sheath. Explore its form and its position within the assembled kit.'
WHERE slug = 'tapak-kasut';