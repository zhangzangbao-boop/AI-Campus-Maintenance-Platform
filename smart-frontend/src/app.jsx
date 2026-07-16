import React from 'react';
import {Routes, Route} from 'react-router-dom';

import StuHome from './Student/stuhome';
import WorkerHome from './Worker/workerhome';
import AdminHome from './Admin/adminhome';
import Login from './Login';
import RepairOrderList from './Admin/RepairOrderList';
import ProtectedRoute from './ProtectedRoute';
import KnowledgeQA from './components/KnowledgeQA';
import './app.css';
function App(){
    return (
        <div className="App">
            <Routes>
                <Route path="/" element={<Login />} />
                <Route path="/login" element={<Login />} />
                <Route path="/stuhome" element={<ProtectedRoute allowedRoles={['STUDENT']}><StuHome /></ProtectedRoute>} />
                <Route path="/workerhome" element={<ProtectedRoute allowedRoles={['STAFF']}><WorkerHome /></ProtectedRoute>} />
                <Route path="/adminhome" element={<ProtectedRoute allowedRoles={['ADMIN']}><AdminHome /></ProtectedRoute>} />
                <Route path="/repairOrders" element={<ProtectedRoute allowedRoles={['ADMIN']}><RepairOrderList repairOrders={undefined} loading={undefined} /></ProtectedRoute>} />
                <Route path="/knowledge-qa" element={<ProtectedRoute allowedRoles={['STUDENT', 'STAFF', 'ADMIN']}><KnowledgeQA /></ProtectedRoute>} />
                <Route path="*" element={<Login />} />
            </Routes>
        </div>
    );
};

export default App;
