import { randomUUID } from 'node:crypto';

const apiUrl = (process.env.PILOT_API_URL ?? '').replace(/\/+$/, '');
const password = process.env.PILOT_DEMO_PASSWORD ?? '';
const ownerEmail = process.env.PILOT_OWNER_EMAIL ?? 'ayah@example.test';
const collaboratorEmail =
  process.env.PILOT_COLLABORATOR_EMAIL ?? 'ibu@example.test';
const expectedSpaceName =
  process.env.PILOT_EXPECTED_SPACE_NAME ?? 'Keluarga Demo';
const mutationAllowed = process.env.PILOT_ALLOW_DUMMY_MUTATION === 'true';

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function validateSafetyBoundary() {
  assert(apiUrl.startsWith('https://'), 'PILOT_API_URL must be an HTTPS URL');
  assert(password.length > 0, 'PILOT_DEMO_PASSWORD is required');
  assert(
    ownerEmail.endsWith('@example.test') &&
      collaboratorEmail.endsWith('@example.test'),
    'Smoke accounts must use the example.test dummy domain',
  );
  assert(
    mutationAllowed,
    'Set PILOT_ALLOW_DUMMY_MUTATION=true to allow the reversible dummy mutation',
  );
}

async function api(path, { token, method = 'GET', body, status = 200 } = {}) {
  const response = await fetch(`${apiUrl}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  const expectedStatuses = Array.isArray(status) ? status : [status];
  if (!expectedStatuses.includes(response.status)) {
    throw new Error(
      `${method} ${path.split('?')[0]} returned ${response.status}; expected ${expectedStatuses.join(' or ')}`,
    );
  }
  return data;
}

async function login(email) {
  return api('/auth/login', {
    method: 'POST',
    body: { email, password },
    status: [200, 201],
  });
}

async function logout(session) {
  if (!session?.refreshToken || !session?.accessToken) return;
  await api('/auth/logout', {
    token: session.accessToken,
    method: 'POST',
    body: { refreshToken: session.refreshToken },
    status: 204,
  });
}

async function listPersons(token, spaceId) {
  return api(`/persons?spaceId=${encodeURIComponent(spaceId)}`, { token });
}

async function patchProfile(token, personId, body, status = 200) {
  return api(`/persons/${personId}/profile`, {
    token,
    method: 'PATCH',
    body,
    status,
  });
}

function result(label, value = 'PASS') {
  process.stdout.write(`${label}: ${value}\n`);
}

validateSafetyBoundary();

let ownerSession;
let collaboratorSession;
let original;
let spaceId;
let targetPersonId;
let changed = false;
let restored = false;
let failed = false;

try {
  const health = await api('/health');
  assert(health?.status === 'ok', 'Cloud health response is not ok');
  result('HTTPS_HEALTH');

  ownerSession = await login(ownerEmail);
  collaboratorSession = await login(collaboratorEmail);
  result('TWO_INDEPENDENT_LOGINS');

  const [ownerSpaces, collaboratorSpaces] = await Promise.all([
    api('/spaces', { token: ownerSession.accessToken }),
    api('/spaces', { token: collaboratorSession.accessToken }),
  ]);
  const ownerSpace = ownerSpaces.find(
    (space) => space.name === expectedSpaceName,
  );
  assert(ownerSpace, `Expected dummy space ${expectedSpaceName} was not found`);
  assert(
    collaboratorSpaces.some((space) => space.spaceId === ownerSpace.spaceId),
    'Collaborator cannot see the owner dummy space',
  );
  spaceId = ownerSpace.spaceId;

  const [ownerPeople, collaboratorPeople] = await Promise.all([
    listPersons(ownerSession.accessToken, spaceId),
    listPersons(collaboratorSession.accessToken, spaceId),
  ]);
  const ownerIds = ownerPeople
    .map((person) => person.personId)
    .sort()
    .join(',');
  const collaboratorIds = collaboratorPeople
    .map((person) => person.personId)
    .sort()
    .join(',');
  assert(ownerPeople.length === 6, 'Expected exactly six dummy profiles');
  assert(
    ownerIds === collaboratorIds,
    'The sessions received different profiles',
  );
  result('SHARED_SIX_PROFILE_SNAPSHOT');

  const target =
    ownerPeople.find(
      (person) => person.personId === process.env.PILOT_TARGET_PERSON_ID,
    ) ?? ownerPeople[0];
  const collaboratorTarget = collaboratorPeople.find(
    (person) => person.personId === target.personId,
  );
  assert(
    collaboratorTarget,
    'Collaborator snapshot is missing the target person',
  );
  assert(
    collaboratorTarget.version === target.version,
    'The initial profile versions differ between sessions',
  );
  targetPersonId = target.personId;
  original = {
    birthPlace: target.birthPlace ?? '',
    notes: target.notes ?? '',
  };

  const runId = randomUUID();
  const ownerMutationId = randomUUID();
  const ownerBody = {
    spaceId,
    birthPlace: original.birthPlace,
    notes: `step7-smoke-owner-${runId}`,
    expectedVersion: target.version,
    clientMutationId: ownerMutationId,
  };
  const ownerUpdate = await patchProfile(
    ownerSession.accessToken,
    targetPersonId,
    ownerBody,
  );
  changed = true;
  assert(
    ownerUpdate.version === target.version + 1,
    'Owner version did not advance',
  );

  const idempotentRetry = await patchProfile(
    ownerSession.accessToken,
    targetPersonId,
    ownerBody,
  );
  assert(
    idempotentRetry.version === ownerUpdate.version,
    'Idempotent retry advanced the version twice',
  );
  result('IDEMPOTENT_RETRY_NO_DUPLICATE_VERSION');

  const collaboratorMutationId = randomUUID();
  const staleBody = {
    spaceId,
    birthPlace: original.birthPlace,
    notes: `step7-smoke-collaborator-${runId}`,
    expectedVersion: collaboratorTarget.version,
    clientMutationId: collaboratorMutationId,
  };
  const conflict = await patchProfile(
    collaboratorSession.accessToken,
    targetPersonId,
    staleBody,
    409,
  );
  assert(
    conflict?.details?.version === ownerUpdate.version,
    'Conflict response did not expose the current server version',
  );
  result('STALE_SESSION_CONFLICT_409');

  const rebasedUpdate = await patchProfile(
    collaboratorSession.accessToken,
    targetPersonId,
    { ...staleBody, expectedVersion: conflict.details.version },
  );
  assert(
    rebasedUpdate.version === ownerUpdate.version + 1,
    'Rebased collaborator mutation did not advance exactly one version',
  );
  result('CONFLICT_REBASE_RETRY');

  const ownerAfter = await listPersons(ownerSession.accessToken, spaceId);
  const ownerVisible = ownerAfter.find(
    (person) => person.personId === targetPersonId,
  );
  assert(
    ownerVisible?.notes === staleBody.notes &&
      ownerVisible.version === rebasedUpdate.version,
    'Owner session cannot observe the collaborator result',
  );
  result('CROSS_SESSION_CHANGE_VISIBLE');

  const restore = await patchProfile(ownerSession.accessToken, targetPersonId, {
    spaceId,
    ...original,
    expectedVersion: rebasedUpdate.version,
    clientMutationId: randomUUID(),
  });
  assert(
    restore.notes === (original.notes || null) &&
      restore.birthPlace === (original.birthPlace || null),
    'Dummy profile restoration did not match the original values',
  );
  changed = false;
  restored = true;
  result('DUMMY_PROFILE_RESTORED');
} catch (error) {
  process.stderr.write(`STEP7_CLOUD_SMOKE_FAILED: ${error.message}\n`);
  failed = true;
} finally {
  if (changed && original && ownerSession && spaceId && targetPersonId) {
    try {
      const latestPeople = await listPersons(ownerSession.accessToken, spaceId);
      const latest = latestPeople.find(
        (person) => person.personId === targetPersonId,
      );
      if (latest) {
        await patchProfile(ownerSession.accessToken, targetPersonId, {
          spaceId,
          ...original,
          expectedVersion: latest.version,
          clientMutationId: randomUUID(),
        });
        restored = true;
        result('DUMMY_PROFILE_RECOVERED_AFTER_FAILURE');
      }
    } catch (cleanupError) {
      process.stderr.write(
        `DUMMY_PROFILE_RECOVERY_FAILED: ${cleanupError.message}\n`,
      );
      failed = true;
    }
  }

  const logoutResults = await Promise.allSettled([
    logout(ownerSession),
    logout(collaboratorSession),
  ]);
  if (logoutResults.some((entry) => entry.status === 'rejected')) {
    process.stderr.write('SESSION_LOGOUT_FAILED\n');
    failed = true;
  } else if (ownerSession || collaboratorSession) {
    result('SESSIONS_LOGGED_OUT');
  }

  if (changed && !restored) failed = true;
  process.exitCode = failed ? 1 : 0;
}
