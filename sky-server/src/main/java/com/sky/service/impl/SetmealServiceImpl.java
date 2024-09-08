package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 套餐业务实现
 */
@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;

    /**
     * 新增套餐，同时需要保存套餐和菜品的关联关系
     * @param setmealDTO
     */
    @Transactional
    @Override
    public void saveWithDish(SetmealDTO setmealDTO) {
        //请求参数用的是SetmealDTO类封装的，包含套餐数据以及套餐菜品关系表数据,
        //   这个地方只需要插入套餐的基本信息，所以进行属性拷贝。
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        //向套餐表插入数据
        setmealMapper.insert(setmeal);

        //获取生成的套餐id   通过sql中的useGeneratedKeys="true" keyProperty="id"获取插入后生成的主键值
        //套餐菜品关系表的setmealId页面不能传递，它是向套餐表插入数据之后生成的主键值，也就是套餐菜品关系表的逻辑外键setmealId
        Long setmealId = setmeal.getId();

        //获取页面传来的套餐和菜品关系表数据
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        //遍历关系表数据，为关系表中的每一条数据(每一个对象)的setmealId赋值，
        //   这个地方不需要像之前写新增菜品时多写个if判断，因为之前的口味数据是非必须的，
        //   这个地方要求套餐必须包含菜品是必须的，所以不需要if判断，不存在套餐不包含菜品得情况
        setmealDishes.forEach(setmealDish -> {
            //将Setmeal套餐类的id属性赋值给SetmealDish套餐关系类的setmealId
            //套餐表的id保存在套餐关系表充当外键为setmealId
            setmealDish.setSetmealId(setmealId);
        });

        //保存套餐和菜品的关联关系  动态sql批量插入
        setmealDishMapper.insertBatch(setmealDishes);
    }

    /**
     * 分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        int pageNum = setmealPageQueryDTO.getPage();
        int pageSize = setmealPageQueryDTO.getPageSize();

        //需要在查询功能之前开启分页功能：当前页的页码   每页显示的条数
        PageHelper.startPage(pageNum, pageSize);
        //这个方法有返回值为Page对象，里面保存的是分页之后的相关数据
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        //封装到PageResult中:总记录数  当前页数据集合
        return new PageResult(page.getTotal(), page.getResult());
    }

}
