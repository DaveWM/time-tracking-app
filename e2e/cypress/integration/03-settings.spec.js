describe('Settings page test', () => {
    beforeEach(() => {
        return cy.setup().then(() => cy.login());
    });

    it('Should be able to set your preferred working hours', () => {
        cy.visit('');

        //cy.get('.uk-spinner').should('exist').should('not.be.visible');
        cy.get('a.uk-button-primary')
            .contains('Settings', {matchCase: false})
            .should('have.length', 1)
            .click();

        cy.url().should('contain', '/settings');

        cy.get('[id="Preferred Working Hours per Day"]').clear().type('5');
        cy.get('form').submit();

        cy.url().should('eq', Cypress.config().baseUrl);
        
        cy.visit('settings');
        cy.get('[id="Preferred Working Hours per Day"]').should('have.value', 5);
    });
})