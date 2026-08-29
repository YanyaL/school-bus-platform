import axios from 'axios';

export async function revokeAccessToken(accessToken: string): Promise<void> {
  await axios.post('/api/v1/auth/logout', undefined, {
    headers: { Authorization: `Bearer ${accessToken}` },
    timeout: 8000,
  });
}
