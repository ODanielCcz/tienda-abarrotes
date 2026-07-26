package com.odcc.tienda.modules.catalog.adapter.config;

import com.odcc.tienda.modules.catalog.application.port.in.ChangeBrandStatusUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ChangeCategoryStatusUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ChangeProductPresentationStatusUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ChangeProductStatusUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.CreateBrandUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.CreateCategoryUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.CreateProductPresentationUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.CreateProductUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.GetBrandByIdUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.GetCategoryByIdUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.GetCategoryTreeByParentUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.GetCategoryTreeUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.GetProductByIdUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.GetProductPresentationByIdUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ListBrandsUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ListCategoriesUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ListProductPresentationsUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.ListProductsUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.UpdateBrandUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.UpdateCategoryUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.UpdateProductPresentationUseCase;
import com.odcc.tienda.modules.catalog.application.port.in.UpdateProductUseCase;
import com.odcc.tienda.modules.catalog.application.port.out.BrandRepositoryPort;
import com.odcc.tienda.modules.catalog.application.port.out.CategoryRepositoryPort;
import com.odcc.tienda.modules.catalog.application.port.out.ProductPresentationRepositoryPort;
import com.odcc.tienda.modules.catalog.application.port.out.ProductRepositoryPort;
import com.odcc.tienda.modules.catalog.application.usecase.ChangeBrandStatusService;
import com.odcc.tienda.modules.catalog.application.usecase.ChangeCategoryStatusService;
import com.odcc.tienda.modules.catalog.application.usecase.ChangeProductPresentationStatusService;
import com.odcc.tienda.modules.catalog.application.usecase.ChangeProductStatusService;
import com.odcc.tienda.modules.catalog.application.usecase.CreateBrandService;
import com.odcc.tienda.modules.catalog.application.usecase.CreateCategoryService;
import com.odcc.tienda.modules.catalog.application.usecase.CreateProductPresentationService;
import com.odcc.tienda.modules.catalog.application.usecase.CreateProductService;
import com.odcc.tienda.modules.catalog.application.usecase.GetBrandByIdService;
import com.odcc.tienda.modules.catalog.application.usecase.GetCategoryByIdService;
import com.odcc.tienda.modules.catalog.application.usecase.GetCategoryTreeByParentService;
import com.odcc.tienda.modules.catalog.application.usecase.GetCategoryTreeService;
import com.odcc.tienda.modules.catalog.application.usecase.GetProductByIdService;
import com.odcc.tienda.modules.catalog.application.usecase.GetProductPresentationByIdService;
import com.odcc.tienda.modules.catalog.application.usecase.ListBrandsService;
import com.odcc.tienda.modules.catalog.application.usecase.ListCategoriesService;
import com.odcc.tienda.modules.catalog.application.usecase.ListProductPresentationsService;
import com.odcc.tienda.modules.catalog.application.usecase.ListProductsService;
import com.odcc.tienda.modules.catalog.application.usecase.UpdateBrandService;
import com.odcc.tienda.modules.catalog.application.usecase.UpdateCategoryService;
import com.odcc.tienda.modules.catalog.application.usecase.UpdateProductPresentationService;
import com.odcc.tienda.modules.catalog.application.usecase.UpdateProductService;
import com.odcc.tienda.shared.application.audit.BusinessAuditPort;
import com.odcc.tienda.shared.application.transaction.TransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CatalogConfiguration {

    @Bean
    CreateBrandUseCase createBrandUseCase(
        BrandRepositoryPort brandRepository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new CreateBrandService(brandRepository, transactionRunner, auditPort);
    }

    @Bean
    GetBrandByIdUseCase getBrandByIdUseCase(BrandRepositoryPort brandRepository) {
        return new GetBrandByIdService(brandRepository);
    }

    @Bean
    ListBrandsUseCase listBrandsUseCase(BrandRepositoryPort brandRepository) {
        return new ListBrandsService(brandRepository);
    }

    @Bean
    UpdateBrandUseCase updateBrandUseCase(
        BrandRepositoryPort brandRepository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new UpdateBrandService(brandRepository, transactionRunner, auditPort);
    }

    @Bean
    ChangeBrandStatusUseCase changeBrandStatusUseCase(
        BrandRepositoryPort brandRepository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new ChangeBrandStatusService(brandRepository, transactionRunner, auditPort);
    }

    @Bean
    CreateCategoryUseCase createCategoryUseCase(
        CategoryRepositoryPort categoryRepository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new CreateCategoryService(categoryRepository, transactionRunner, auditPort);
    }

    @Bean
    GetCategoryByIdUseCase getCategoryByIdUseCase(CategoryRepositoryPort categoryRepository) {
        return new GetCategoryByIdService(categoryRepository);
    }

    @Bean
    ListCategoriesUseCase listCategoriesUseCase(CategoryRepositoryPort categoryRepository) {
        return new ListCategoriesService(categoryRepository);
    }

    @Bean
    GetCategoryTreeUseCase getCategoryTreeUseCase(CategoryRepositoryPort categoryRepository) {
        return new GetCategoryTreeService(categoryRepository);
    }

    @Bean
    GetCategoryTreeByParentUseCase getCategoryTreeByParentUseCase(CategoryRepositoryPort categoryRepository) {
        return new GetCategoryTreeByParentService(categoryRepository);
    }

    @Bean
    UpdateCategoryUseCase updateCategoryUseCase(
        CategoryRepositoryPort categoryRepository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new UpdateCategoryService(categoryRepository, transactionRunner, auditPort);
    }

    @Bean
    ChangeCategoryStatusUseCase changeCategoryStatusUseCase(
        CategoryRepositoryPort categoryRepository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new ChangeCategoryStatusService(categoryRepository, transactionRunner, auditPort);
    }

    @Bean
    CreateProductUseCase createProductUseCase(
        ProductRepositoryPort productRepository,
        CategoryRepositoryPort categoryRepository,
        BrandRepositoryPort brandRepository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new CreateProductService(productRepository, categoryRepository, brandRepository, transactionRunner, auditPort);
    }

    @Bean
    GetProductByIdUseCase getProductByIdUseCase(ProductRepositoryPort productRepository) {
        return new GetProductByIdService(productRepository);
    }

    @Bean
    ListProductsUseCase listProductsUseCase(ProductRepositoryPort productRepository) {
        return new ListProductsService(productRepository);
    }

    @Bean
    UpdateProductUseCase updateProductUseCase(
        ProductRepositoryPort productRepository,
        CategoryRepositoryPort categoryRepository,
        BrandRepositoryPort brandRepository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new UpdateProductService(productRepository, categoryRepository, brandRepository, transactionRunner, auditPort);
    }

    @Bean
    ChangeProductStatusUseCase changeProductStatusUseCase(
        ProductRepositoryPort productRepository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new ChangeProductStatusService(productRepository, transactionRunner, auditPort);
    }

    @Bean
    CreateProductPresentationUseCase createProductPresentationUseCase(
        ProductPresentationRepositoryPort presentationRepository,
        ProductRepositoryPort productRepository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new CreateProductPresentationService(presentationRepository, productRepository, transactionRunner, auditPort);
    }

    @Bean
    GetProductPresentationByIdUseCase getProductPresentationByIdUseCase(ProductPresentationRepositoryPort presentationRepository) {
        return new GetProductPresentationByIdService(presentationRepository);
    }

    @Bean
    ListProductPresentationsUseCase listProductPresentationsUseCase(
        ProductPresentationRepositoryPort presentationRepository,
        ProductRepositoryPort productRepository
    ) {
        return new ListProductPresentationsService(presentationRepository, productRepository);
    }

    @Bean
    UpdateProductPresentationUseCase updateProductPresentationUseCase(
        ProductPresentationRepositoryPort presentationRepository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new UpdateProductPresentationService(presentationRepository, transactionRunner, auditPort);
    }

    @Bean
    ChangeProductPresentationStatusUseCase changeProductPresentationStatusUseCase(
        ProductPresentationRepositoryPort presentationRepository,
        TransactionRunner transactionRunner,
        BusinessAuditPort auditPort
    ) {
        return new ChangeProductPresentationStatusService(presentationRepository, transactionRunner, auditPort);
    }
}
