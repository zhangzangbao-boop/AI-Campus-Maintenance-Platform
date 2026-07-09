import { Navigate, useLocation } from 'react-router-dom';

const ROLE_HOME_PATH = {
  STUDENT: '/stuhome',
  STAFF: '/workerhome',
  ADMIN: '/adminhome',
};

export const normalizeRole = (role) => {
  if (!role) {
    return '';
  }
  return String(role).replace(/^ROLE_/i, '').toUpperCase();
};

export const getHomePathByRole = (role) => {
  return ROLE_HOME_PATH[normalizeRole(role)] || '/login';
};

const readStoredUser = () => {
  const token = localStorage.getItem('token');
  const userText = localStorage.getItem('user');

  if (!token || !userText) {
    return null;
  }

  try {
    const user = JSON.parse(userText);
    return { token, user, role: normalizeRole(user?.role) };
  } catch {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    return null;
  }
};

function ProtectedRoute({ allowedRoles, children }) {
  const location = useLocation();
  const session = readStoredUser();

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  const normalizedAllowedRoles = allowedRoles.map(normalizeRole);
  if (!normalizedAllowedRoles.includes(session.role)) {
    return <Navigate to={getHomePathByRole(session.role)} replace />;
  }

  return children;
}

export default ProtectedRoute;
