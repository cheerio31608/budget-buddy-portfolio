-- Upgrade only the untouched seed credential. Existing user-managed hashes are preserved.
-- Demo login: user1@test.com / password
UPDATE users
SET password_hash = '{bcrypt}$2a$10$uPEoV02JOuRL7uOcEGO/6OksPXflQeS9kwdbPnoDrc0AA33n3hrh.'
WHERE email = 'user1@test.com'
  AND password_hash = 'pw_hash_value_here';
