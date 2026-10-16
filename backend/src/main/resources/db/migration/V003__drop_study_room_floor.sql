-- 删除自习室「楼层」属性：界面与业务均已移除 floorNo，物理列一并去掉。
ALTER TABLE study_room DROP COLUMN floor_no;
