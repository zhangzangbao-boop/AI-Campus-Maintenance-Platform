import React from 'react';
import '@ant-design/v5-patch-for-react-19'; 
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './app';

if (import.meta.env.PROD) {
  console.log = () => {};
  console.debug = () => {};
}

const root = document.getElementById('root');
if (!root) {
  throw new Error('Root element not found: please ensure <div id="root"></div> exists in your index.html');
}
ReactDOM.createRoot(root).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
