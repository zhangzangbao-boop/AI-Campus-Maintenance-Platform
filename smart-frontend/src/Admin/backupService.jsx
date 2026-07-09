import { message } from 'antd';
import api from '../services/api';

const handle = async (fn, successMsg) => {
  try {
    const res = await fn();

    if (res && res.code === 200) {
      if (successMsg) {
        message.success(successMsg);
      }
      return res.data || [];
    }
    // 对于删除操作，可能返回空对象（204 No Content）
    if (res && (res.code === 200 || res.message === '备份已删除' || Object.keys(res).length === 0)) {
      if (successMsg) {
        message.success(successMsg);
      }
      return res.data || [];
    }
    throw new Error(res?.message || '请求失败');
  } catch (err) {
    console.error('备份服务错误:', err);
    message.error(err.message || '操作失败');
    throw err;
  }
};

export const backupService = {
  create: () => handle(() => api.admin.createBackup(), '备份已创建'),
  list: () => handle(() => api.admin.listBackups()),
  status: () => handle(() => api.admin.getBackupStatus()),
  restore: (fileName) =>
    handle(() => api.admin.restoreBackup(fileName), '恢复操作已执行，系统已在恢复前创建保护备份'),
  remove: (fileName) => handle(() => api.admin.deleteBackup(fileName), '备份已删除'),
};

