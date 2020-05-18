<#include "header.ftl">

<div class="d-flex flex-column flex-md-row align-items-center p-3 px-md-4 mb-3 bg-white border-bottom shadow-sm">
    <h1 class="my-0 mr-md-auto font-weight-normal display-1">Wiki home</h1>

    <form action="/create" method="post">
        <div class="form-row align-items-center">
            <div class="col-auto">
                <label class="sr-only" for="name">Name</label>
                <input type="text" class="form-control mb-2" id="name" name="name" placeholder="Page name">
            </div>
            <div class="col-auto">
                <button type="submit" class="btn btn-primary mb-2">Create</button>
            </div>
        </div>
    </form>
</div>

<div class="container">
    <div class="row">
        <h2>Pages:</h2>
    </div>
    <div class="row">
        <#if pages?has_content>
            <ul>
                <#list pages as page>
                    <li>
                        <a href="/wiki/${page.key}">${page.value}</a>
                    </li>
                </#list>
            </ul>
        <#else>
            <p>The Wiki is empty! Create new page</p>
        </#if>
    </div>
</div>

<#include "footer.ftl">