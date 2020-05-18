<#include "header.ftl">

<div class="d-flex flex-column flex-md-row align-items-center p-3 px-md-4 mb-3 bg-white border-bottom shadow-sm">
    <h1 class="my-0 mr-md-auto font-weight-normal display-1">{ ${name} }</h1>

    <a class="btn btn-outline-primary" href="/" role="button" aria-pressed="true">Home</a>
</div>

<div class="container">
    <form action="/save" method="post">
        <div class="form-group">
            <input type="hidden" name="id" value="${id}">
            <input type="hidden" name="name" value="${name}">
            <textarea class="form-control" id="markdown" name="markdown" rows="15">${content}</textarea>
        </div>
        <button type="submit" class="btn btn-primary">Save</button>
        <button type="submit" formaction="/delete" class="btn btn-danger float-right">Delete</button>
    </form>
</div>

<#include "footer.ftl">